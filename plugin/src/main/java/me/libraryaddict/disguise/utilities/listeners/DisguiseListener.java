package me.libraryaddict.disguise.utilities.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import lombok.Getter;
import lombok.Setter;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.LibsDisguises;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.disguisetypes.TargetedDisguise;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.LibsEntityInteract;
import me.libraryaddict.disguise.utilities.LibsPremium;
import me.libraryaddict.disguise.utilities.modded.ModdedEntity;
import me.libraryaddict.disguise.utilities.modded.ModdedManager;
import me.libraryaddict.disguise.utilities.reflection.NmsVersion;
import me.libraryaddict.disguise.utilities.reflection.ReflectionManager;
import me.libraryaddict.disguise.utilities.translations.LibsMsg;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public class DisguiseListener implements Listener {
    private final HashMap<String, LibsEntityInteract> interactions = new HashMap<>();
    private final HashMap<String, BukkitRunnable> disguiseRunnable = new HashMap<>();
    private final LibsDisguises plugin;
    @Getter
    @Setter
    private boolean isDodgyUser;

    public DisguiseListener(LibsDisguises libsDisguises) {
        plugin = libsDisguises;

        runUpdateScheduler();

        //if (!LibsPremium.getPluginInformation().isPremium() || LibsPremium.getPluginInformation().getUserID().matches("\\d+")) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        //}

        if (!DisguiseConfig.isSaveEntityDisguises()) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                Disguise[] disguises = DisguiseUtilities.getSavedDisguises(entity, true);

                if (disguises.length <= 0) {
                    continue;
                }

                DisguiseUtilities.resetPluginTimer();

                for (Disguise disguise : disguises) {
                    disguise.setEntity(entity);
                    disguise.startDisguise();
                }
            }
        }
    }

    private boolean isCheckReleases() {
        if (DisguiseConfig.getUpdatesBranch() == DisguiseConfig.UpdatesBranch.RELEASES) {
            return true;
        }

        if (DisguiseConfig.getUpdatesBranch() == DisguiseConfig.UpdatesBranch.SAME_BUILDS && plugin.isReleaseBuild()) {
            return true;
        }

        // If build number is null, or not a number. Then we can't check snapshots regardless
        return !plugin.isNumberedBuild();
    }

    private void runUpdateScheduler() {
        if (!DisguiseConfig.isNotifyUpdate()) {
            return;
        }

        if (DisguiseConfig.isAutoUpdate() && !isCheckReleases()) {
            DisguiseUtilities.getLogger().info("Plugin will attempt to auto update when new builds are ready! Check config to disable.");
        }
    }

    public void cleanup() {
        for (BukkitRunnable r : disguiseRunnable.values()) {
            r.cancel();
        }

        interactions.clear();
    }

    private void checkPlayerCanBlowDisguise(Player player) {
        Disguise[] disguises = DisguiseAPI.getDisguises(player);

        if (disguises.length > 0) {
            DisguiseAPI.undisguiseToAll(player);

            LibsMsg.BLOWN_DISGUISE.send(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        DisguiseUtilities.setPlayerVelocity(event.getPlayer());

        /*if (LibsPremium.getUserID().equals("" + (10000 + 2345))) {
            event.setVelocity(event.getVelocity().multiply(5));
        } else if (isDodgyUser()) {
            event.setVelocity(event.getVelocity().multiply(0.75 + (event.getPlayer().getExp() * 1.5)));
        }*/
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();

        if (attacker instanceof Projectile && ((Projectile) attacker).getShooter() instanceof Player) {
            attacker = (Entity) ((Projectile) attacker).getShooter();
        }

        /*if ("%%__USER__%%".equals("12345")) {
            event.setDamage(0.5);
            event.setCancelled(false);
        }*/

        if (event.getEntityType() != EntityType.PLAYER && !(attacker instanceof Player)) {
            return;
        }

        if (event.getEntity() instanceof Player) {
            if (DisguiseConfig.isDisguiseBlownWhenAttacked()) {
                checkPlayerCanBlowDisguise((Player) event.getEntity());
            }
        }

        checkPlayerCanFight(event, attacker);

        if (attacker instanceof Player) {
            if (DisguiseConfig.isDisguiseBlownWhenAttacking()) {
                checkPlayerCanBlowDisguise((Player) attacker);
            }
        }
    }

    private boolean canRetaliate(Entity entity) {
        return entity.hasMetadata("LD-LastAttacked") &&
            entity.getMetadata("LD-LastAttacked").get(0).asLong() + (DisguiseConfig.getPvPTimer() * 1000) > System.currentTimeMillis();
    }

    private void setRetaliation(Entity entity) {
        entity.removeMetadata("LD-LastAttacked", LibsDisguises.getInstance());
        entity.setMetadata("LD-LastAttacked", new FixedMetadataValue(LibsDisguises.getInstance(), System.currentTimeMillis()));
    }

    private void checkPlayerCanFight(EntityDamageByEntityEvent event, Entity attacker) {
        // If both are players, check if allowed pvp, else if allowed pve
        boolean pvp = attacker instanceof Player && event.getEntity() instanceof Player;

        if (pvp ? !DisguiseConfig.isDisablePvP() : !DisguiseConfig.isDisablePvE()) {
            return;
        }

        if (!attacker.hasPermission("libsdisguises." + (pvp ? "pvp" : "pve")) && !attacker.hasPermission("libsdisguises." + (pvp ? "pvp" : "pve"))) {
            if (!DisguiseConfig.isRetaliationCombat() || !canRetaliate(attacker)) {
                Disguise[] disguises = DisguiseAPI.getDisguises(attacker);

                if (disguises.length > 0) {
                    event.setCancelled(true);

                    LibsMsg.CANT_ATTACK_DISGUISED.send(attacker);
                } else if (DisguiseConfig.getPvPTimer() > 0 && attacker.hasMetadata("LastDisguise")) {
                    long lastDisguised = attacker.getMetadata("LastDisguise").get(0).asLong();

                    if (lastDisguised + DisguiseConfig.getPvPTimer() * 1000 > System.currentTimeMillis()) {
                        event.setCancelled(true);

                        LibsMsg.CANT_ATTACK_DISGUISED_RECENTLY.send(attacker);
                    }
                }
            }
        }

        if (!event.isCancelled() && DisguiseConfig.isRetaliationCombat()) {
            setRetaliation(event.getEntity());
            setRetaliation(attacker);
        }
    }

    /*@EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!isDodgyUser() && !"%%__USER__%%".equals(12 + "345")) {
            return;
        }

        if (event.isCancelled()) {
            event.setCancelled(false);
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setDamage(event.getDamage() * 3);
        } else {
            event.setDamage(new Random().nextDouble() * 8);
        }
    }*/

    @EventHandler
    public void onHeldItemSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Disguise disguise = DisguiseAPI.getDisguise(player, player);

        if (disguise == null || !disguise.isHidingHeldItemFromSelf()) {
            return;
        }

        // From logging, it seems that both bukkit and nms uses the same thing for the slot switching.
        // 0 1 2 3 - 8
        // If the packet is coming, then I need to replace the item they are switching to
        // As for the old item, I need to restore it.
        org.bukkit.inventory.ItemStack currentlyHeld = player.getItemInHand();
        // If his old weapon isn't air
        if (currentlyHeld != null && currentlyHeld.getType() != Material.AIR) {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.SET_SLOT);

            StructureModifier<Object> mods = packet.getModifier();

            mods.write(0, 0);
            mods.write(NmsVersion.v1_17.isSupported() ? 2 : 1, event.getPreviousSlot() + 36);

            if (NmsVersion.v1_17.isSupported()) {
                mods.write(1, ReflectionManager.getIncrementedStateId(player));
            }

            packet.getItemModifier().write(0, currentlyHeld);

            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet, false);
        }

        org.bukkit.inventory.ItemStack newHeld = player.getInventory().getItem(event.getNewSlot());

        // If his new weapon isn't air either!
        if (newHeld != null && newHeld.getType() != Material.AIR) {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.SET_SLOT);

            StructureModifier<Object> mods = packet.getModifier();

            mods.write(0, 0);
            mods.write(NmsVersion.v1_17.isSupported() ? 2 : 1, event.getNewSlot() + 36);

            if (NmsVersion.v1_17.isSupported()) {
                mods.write(1, ReflectionManager.getIncrementedStateId(player));
            }

            packet.getItemModifier().write(0, new ItemStack(Material.AIR));

            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet, false);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!DisguiseConfig.isSaveEntityDisguises() || NmsVersion.v1_18.isSupported()) {
            return;
        }

        for (Entity entity : event.getChunk().getEntities()) {
            Disguise[] disguises = DisguiseAPI.getDisguises(entity);

            if (disguises.length <= 0) {
                continue;
            }

            DisguiseUtilities.saveDisguises(entity, disguises);
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        if (!DisguiseConfig.isSaveEntityDisguises()) {
            return;
        }

        int disguisesSaved = 0;

        for (Entity entity : event.getWorld().getEntities()) {
            if (entity instanceof Player) {
                continue;
            }

            Disguise[] disguises = DisguiseAPI.getDisguises(entity);

            if (disguises.length <= 0) {
                continue;
            }

            disguisesSaved++;
            DisguiseUtilities.saveDisguises(entity, disguises);
        }

        if (disguisesSaved > 0) {
            DisguiseUtilities.getLogger().info("World unloaded, saved " + disguisesSaved + " disguises");
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!DisguiseConfig.isSaveEntityDisguises() || NmsVersion.v1_18.isSupported()) {
            return;
        }

        for (Entity entity : event.getChunk().getEntities()) {
            Disguise[] disguises = DisguiseUtilities.getSavedDisguises(entity, true);

            if (disguises.length <= 0) {
                continue;
            }

            DisguiseUtilities.resetPluginTimer();

            for (Disguise disguise : disguises) {
                disguise.setEntity(entity);
                disguise.startDisguise();
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!DisguiseConfig.isSaveEntityDisguises()) {
            return;
        }

        for (Entity entity : event.getWorld().getEntities()) {
            Disguise[] disguises = DisguiseUtilities.getSavedDisguises(entity, true);

            if (disguises.length <= 0) {
                continue;
            }

            DisguiseUtilities.resetPluginTimer();

            for (Disguise disguise : disguises) {
                disguise.setEntity(entity);
                disguise.startDisguise();
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        p.removeMetadata("ld_loggedin", LibsDisguises.getInstance());
        //plugin.getUpdateChecker().notifyUpdate(p);

        String requiredProtocolLib = StringUtils.join(DisguiseUtilities.getProtocolLibRequiredVersion(), " or build #");
        String version = ProtocolLibrary.getPlugin().getDescription().getVersion();

        if (DisguiseUtilities.isProtocolLibOutdated() && p.isOp()) {
            DisguiseUtilities.sendProtocolLibUpdateMessage(p, version, requiredProtocolLib);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline()) {
                        cancel();
                        return;
                    }

                    DisguiseUtilities.sendProtocolLibUpdateMessage(p, version, requiredProtocolLib);
                }
            }.runTaskTimer(LibsDisguises.getInstance(), 10, 10 * 60 * 20); // Run every 10 minutes
        }

        if (DisguiseConfig.isSavePlayerDisguises()) {
            Disguise[] disguises = DisguiseUtilities.getSavedDisguises(p, true);

            if (disguises.length > 0) {
                DisguiseUtilities.resetPluginTimer();
            }

            for (Disguise disguise : disguises) {
                disguise.setEntity(p);
                disguise.startDisguise();
            }
        }

        for (Set<TargetedDisguise> disguiseList : DisguiseUtilities.getDisguises().values()) {
            for (TargetedDisguise targetedDisguise : disguiseList) {
                if (targetedDisguise.getEntity() == null) {
                    continue;
                }

                // Removed as its not compatible with scoreboard teams
                /*if (p.hasPermission("libsdisguises.seethrough") &&
                        targetedDisguise.getDisguiseTarget() == TargetedDisguise.TargetType.SHOW_TO_EVERYONE_BUT_THESE_PLAYERS) {
                    targetedDisguise.addPlayer(p);
                }*/

                if (!targetedDisguise.canSee(p)) {
                    continue;
                }

                if (!(targetedDisguise instanceof PlayerDisguise)) {
                    continue;
                }

                PlayerDisguise disguise = (PlayerDisguise) targetedDisguise;

                if (disguise.isDisplayedInTab()) {
                    ProtocolLibrary.getProtocolManager().sendServerPacket(p, ReflectionManager.createTablistAddPackets(disguise));
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    return;
                }

                DisguiseUtilities.registerNoName(p.getScoreboard());

                if (p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                    DisguiseUtilities.registerAllExtendedNames(p.getScoreboard());
                    DisguiseUtilities.registerColors(p.getScoreboard());
                }

                if (!p.hasMetadata("forge_mods")) {
                    Optional<ModdedEntity> required =
                        ModdedManager.getEntities().values().stream().filter(c -> c.getMod() != null && c.getRequired() != null).findAny();

                    required.ifPresent(customEntity -> p.kickPlayer(customEntity.getRequired()));
                }

                if (DisguiseConfig.isSaveGameProfiles() && DisguiseConfig.isUpdateGameProfiles() && DisguiseUtilities.hasGameProfile(p.getName())) {
                    WrappedGameProfile profile = WrappedGameProfile.fromPlayer(p);

                    if (!profile.getProperties().isEmpty()) {
                        DisguiseUtilities.addGameProfile(p.getName(), profile);
                    }
                }
            }
        }.runTaskLater(LibsDisguises.getInstance(), 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    return;
                }

                if (!p.hasMetadata("forge_mods")) {
                    Optional<ModdedEntity> required =
                        ModdedManager.getEntities().values().stream().filter(c -> c.getMod() != null && c.getRequired() != null).findAny();

                    required.ifPresent(customEntity -> p.kickPlayer(customEntity.getRequired()));
                }
            }
        }.runTaskLater(LibsDisguises.getInstance(), 60);
    }

    /**
     * Most likely faster if we don't bother doing checks if he sees a player disguise
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // If yer a pirate with a pirated jar, sometimes you can't move
        /*if (("%%__USER__%%".isEmpty() || DisguiseUtilities.isInvalidFile()) && !event.getPlayer().isOp() && RandomUtils.nextDouble() < 0.01) {
            event.setCancelled(true);
        }*/

        // If the bounding boxes are modified and the player moved more than a little
        // The runnable in Disguise also calls it, so we should ignore smaller movements
        if (DisguiseConfig.isModifyBoundingBox() && event.getFrom().distanceSquared(event.getTo()) > 0.2) {
            // Only fetching one disguise as we cannot modify the bounding box of multiple disguises
            Disguise disguise = DisguiseAPI.getDisguise(event.getPlayer());

            // If disguise doesn't exist, or doesn't modify bounding box
            if (disguise == null || !disguise.isModifyBoundingBox()) {
                return;
            }

            // Modify bounding box
            DisguiseUtilities.doBoundingBox((TargetedDisguise) disguise);
        }

        if (DisguiseConfig.isStopShulkerDisguisesFromMoving()) {
            Disguise disguise;

            if ((disguise = DisguiseAPI.getDisguise(event.getPlayer())) != null) {
                if (disguise.getType() == DisguiseType.SHULKER) { // Stop Shulker disguises from moving their coordinates
                    Location from = event.getFrom();
                    Location to = event.getTo();

                    to.setX(from.getX());
                    to.setZ(from.getZ());

                    event.setTo(to);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        for (String meta : new String[]{"LastDisguise", "LD-LastAttacked", "forge_mods", "LibsRabbitHop", "ld_loggedin"}) {
            player.removeMetadata(meta, LibsDisguises.getInstance());
        }

        // Removed as its not compatible with scoreboard teams
        /*if (player.hasPermission("libsdisguises.seethrough")) {
            for (Set<TargetedDisguise> disguises : DisguiseUtilities.getDisguises().values()) {
                for (TargetedDisguise disguise : disguises) {
                    if (disguise.getDisguiseTarget() != TargetedDisguise.TargetType.SHOW_TO_EVERYONE_BUT_THESE_PLAYERS) {
                        continue;
                    }

                    disguise.silentlyRemovePlayer(player.getName());
                }
            }
        }*/

        if (!DisguiseConfig.isSavePlayerDisguises()) {
            return;
        }

        Disguise[] disguises = DisguiseAPI.getDisguises(player);

        if (disguises.length <= 0) {
            return;
        }

        DisguiseUtilities.saveDisguises(player, disguises);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();

        if (!interactions.containsKey(p.getName())) {
            if (isDodgyUser() && System.currentTimeMillis() % 6 == 0 && !p.getAllowFlight() && p.getPreviousGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }

            return;
        }

        event.setCancelled(true);
        disguiseRunnable.remove(p.getName()).cancel();

        Entity entity = event.getRightClicked();
        interactions.remove(p.getName()).onInteract(p, entity);
    }

    @EventHandler
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        Player p = event.getPlayer();

        if (!interactions.containsKey(p.getName())) {
            return;
        }

        event.setCancelled(true);
        disguiseRunnable.remove(p.getName()).cancel();

        Entity entity = event.getRightClicked();
        interactions.remove(p.getName()).onInteract(p, entity);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() == null) {
            return;
        }

        /*if (LibsPremium.isPremium() && !LibsPremium.isBisectHosted() && LibsPremium.getPaidInformation() != null &&
            LibsPremium.getPaidInformation().getSize() == 0) {
            event.setCancelled(true);
            return;
        }*/

        switch (event.getReason()) {
            case TARGET_ATTACKED_ENTITY:
                /*if (LibsPremium.isBisectHosted() && !Bukkit.getIp().matches("((25[0-5]|(2[0-4]|1\\d|[1-9]|)[0-9])(\\.(?!$)|$)){4}")) {
                    event.setCancelled(true);
                }*/
            case TARGET_ATTACKED_OWNER:
            case OWNER_ATTACKED_TARGET:
            case CUSTOM:
                return;
            default:
                break;
        }

        Disguise disguise = DisguiseAPI.getDisguise(event.getTarget());

        if (disguise == null) {
            return;
        }

        if (disguise.isMobsIgnoreDisguise()) {
            event.setCancelled(true);
        } else if (DisguiseConfig.isMonstersIgnoreDisguises() && event.getTarget() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        final Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        /*if (!player.isOp() && !player.hasPermission("minecraft.command.teleport") && LibsPremium.getPaidInformation() != null &&
            LibsPremium.getPaidInformation().getUserID().equals("1592")) {
            player.sendMessage(ChatColor.GOLD + "Your teleport was a success!");
        }*/

        if (!DisguiseAPI.isDisguised(player)) {
            return;
        }

        if (DisguiseConfig.isUndisguiseOnWorldChange() && to.getWorld() != null && from.getWorld() != null && to.getWorld() != from.getWorld()) {
            Disguise[] disguises = DisguiseAPI.getDisguises(event.getPlayer());

            if (disguises.length > 0) {
                for (Disguise disguise : disguises) {
                    disguise.removeDisguise();
                }

                LibsMsg.SWITCH_WORLD_DISGUISE_REMOVED.send(event.getPlayer());
            }
        }

        if (DisguiseAPI.isSelfDisguised(player) && to.getWorld() == from.getWorld()) {
            Disguise disguise = DisguiseAPI.getDisguise(player, player);

            // If further than 64 blocks, resend the self disguise
            if (disguise != null && disguise.isSelfDisguiseVisible() && from.distanceSquared(to) > 4096) {
                // Send a packet to destroy the fake entity so that we can resend it without glitches
                PacketContainer packet = DisguiseUtilities.getDestroyPacket(DisguiseAPI.getSelfDisguiseId());

                try {
                    ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (event.isCancelled() || !disguise.isDisguiseInUse()) {
                            return;
                        }

                        DisguiseUtilities.sendSelfDisguise(player, (TargetedDisguise) disguise);
                    }
                }.runTaskLater(LibsDisguises.getInstance(), 4);
            }
        } else if (from.getWorld() != to.getWorld()) {
            // Stupid hack to fix worldswitch invisibility bug & paper packet bug
            final boolean viewSelfToggled = DisguiseAPI.isViewSelfToggled(event.getPlayer());

            if (viewSelfToggled) {
                final Disguise disguise = DisguiseAPI.getDisguise(event.getPlayer());

                if (disguise != null && disguise.isSelfDisguiseVisible()) {
                    disguise.setViewSelfDisguise(false);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> disguise.setViewSelfDisguise(true), 20L);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) {
            return;
        }

        Disguise disguise = DisguiseAPI.getDisguise((Player) event.getEntered(), event.getEntered());

        if (disguise == null) {
            return;
        }

        DisguiseUtilities.removeSelfDisguise(disguise);

        ((Player) event.getEntered()).updateInventory();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleLeave(VehicleExitEvent event) {
        if (event.getExited() instanceof Player) {
            final Disguise disguise = DisguiseAPI.getDisguise((Player) event.getExited(), event.getExited());

            if (disguise != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    DisguiseUtilities.setupFakeDisguise(disguise);

                    ((Player) disguise.getEntity()).updateInventory();
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldSwitch(final PlayerChangedWorldEvent event) {
        if (!DisguiseAPI.isDisguised(event.getPlayer())) {
            return;
        }

        if (DisguiseConfig.isUndisguiseOnWorldChange()) {
            Disguise[] disguises = DisguiseAPI.getDisguises(event.getPlayer());

            if (disguises.length > 0) {
                for (Disguise disguise : disguises) {
                    disguise.removeDisguise();
                }

                LibsMsg.SWITCH_WORLD_DISGUISE_REMOVED.send(event.getPlayer());
            }
        } else {
            // Stupid hack to fix worldswitch invisibility bug & paper packet bug
            final boolean viewSelfToggled = DisguiseAPI.isViewSelfToggled(event.getPlayer());

            if (viewSelfToggled) {
                final Disguise disguise = DisguiseAPI.getDisguise(event.getPlayer());

                if (disguise != null && disguise.isSelfDisguiseVisible()) {
                    disguise.setViewSelfDisguise(false);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> disguise.setViewSelfDisguise(true), 20L);
                }
            }
        }
    }

    public void addInteraction(String playerName, LibsEntityInteract interaction, int secondsExpire) {
        if (disguiseRunnable.containsKey(playerName)) {
            disguiseRunnable.get(playerName).cancel();
        }

        interactions.put(playerName, interaction);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                interactions.remove(playerName);
                disguiseRunnable.remove(playerName);
            }
        };

        runnable.runTaskLater(LibsDisguises.getInstance(), secondsExpire * 20L);

        disguiseRunnable.put(playerName, runnable);
    }
}
