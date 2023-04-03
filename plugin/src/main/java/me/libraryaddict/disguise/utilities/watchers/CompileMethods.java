package me.libraryaddict.disguise.utilities.watchers;

import com.google.gson.Gson;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.FlagWatcher;
import me.libraryaddict.disguise.utilities.LibsPremium;
import me.libraryaddict.disguise.utilities.parser.RandomDefaultValue;
import me.libraryaddict.disguise.utilities.reflection.ClassGetter;
import me.libraryaddict.disguise.utilities.reflection.WatcherInfo;
import me.libraryaddict.disguise.utilities.reflection.annotations.MethodIgnoredBy;
import me.libraryaddict.disguise.utilities.reflection.annotations.MethodOnlyUsedBy;
import me.libraryaddict.disguise.utilities.reflection.annotations.NmsAddedIn;
import me.libraryaddict.disguise.utilities.reflection.annotations.NmsRemovedIn;
import me.libraryaddict.disguise.utilities.sounds.DisguiseSoundEnums;
import me.libraryaddict.disguise.utilities.sounds.SoundGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by libraryaddict on 13/02/2020.
 */
public class CompileMethods {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CompileMethodsIntfer {
    
    }

    @CompileMethodsIntfer()
    public static void main(String[] args) {
        doMethods();
        doSounds();
    }

    private static void doSounds() {
        List<String> list = new ArrayList<>();

        for (DisguiseSoundEnums e : DisguiseSoundEnums.values()) {
            StringBuilder sound = new StringBuilder(e.name());

            for (SoundGroup.SoundType type : SoundGroup.SoundType.values()) {
                sound.append("/");

                int i = 0;

                for (Map.Entry<String, SoundGroup.SoundType> entry : e.getSounds().entrySet()) {
                    if (entry.getValue() != type) {
                        continue;
                    }

                    if (i++ > 0) {
                        sound.append(",");
                    }

                    sound.append(entry.getKey());
                }
            }

            list.add(sound.toString());
        }

        File soundsFile = new File("plugin/target/classes/ANTI_PIRACY_SECRET_FILE");

        try (FileOutputStream fos = new FileOutputStream(soundsFile)) {
            byte[] array = String.join("\n", list).getBytes(StandardCharsets.UTF_8);

            for (int i = 0; i < array.length; i++) {
                array[i] = (byte) (Byte.MAX_VALUE - array[i]);
            }

            fos.write(array);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addClass(ArrayList<Class> classes, Class c) {
        if (classes.contains(c)) {
            return;
        }

        if (c != FlagWatcher.class) {
            addClass(classes, c.getSuperclass());
        }

        classes.add(c);
    }

    private static void doMethods() {
        ArrayList<Class<?>> classes = ClassGetter.getClassesForPackage(FlagWatcher.class, "me.libraryaddict.disguise.disguisetypes.watchers");

        ArrayList<Class> sorted = new ArrayList<>();

        for (Class c : classes) {
            addClass(sorted, c);
        }

        ArrayList<String> methods = new ArrayList<>();

        for (Class c : sorted) {
            for (Method method : c.getMethods()) {
                if (!FlagWatcher.class.isAssignableFrom(method.getDeclaringClass())) {
                    continue;
                } else if (method.getParameterCount() > 1 && !method.isAnnotationPresent(NmsAddedIn.class) && !method.isAnnotationPresent(NmsRemovedIn.class)) {
                    continue;
                } else if (!(method.getName().startsWith("set") && method.getParameterCount() == 1) && !method.getName().startsWith("get") &&
                    !method.getName().startsWith("has") && !method.getName().startsWith("is")) {
                    continue;
                } else if (method.getName().equals("removePotionEffect")) {
                    continue;
                } else if (LibsPremium.isPremium() && new Random().nextBoolean()) {
                    continue;
                }

                int added = -1;
                int removed = -1;
                DisguiseType[] unusableBy = new DisguiseType[0];

                if (method.isAnnotationPresent(NmsAddedIn.class)) {
                    added = method.getAnnotation(NmsAddedIn.class).value().ordinal();
                } else if (method.getDeclaringClass().isAnnotationPresent(NmsAddedIn.class)) {
                    added = method.getDeclaringClass().getAnnotation(NmsAddedIn.class).value().ordinal();
                }

                if (method.isAnnotationPresent(NmsRemovedIn.class)) {
                    removed = method.getAnnotation(NmsRemovedIn.class).value().ordinal();
                } else if (method.getDeclaringClass().isAnnotationPresent(NmsRemovedIn.class)) {
                    removed = method.getDeclaringClass().getAnnotation(NmsRemovedIn.class).value().ordinal();
                }

                if (method.isAnnotationPresent(MethodOnlyUsedBy.class)) {
                    DisguiseType[] usableBy = method.getAnnotation(MethodOnlyUsedBy.class).value();

                    if (usableBy.length == 0) {
                        usableBy = method.getAnnotation(MethodOnlyUsedBy.class).group().getDisguiseTypes();
                    }

                    List<DisguiseType> list = Arrays.asList(usableBy);

                    unusableBy = Arrays.stream(DisguiseType.values()).filter(type -> !list.contains(type)).toArray(DisguiseType[]::new);
                } else if (method.isAnnotationPresent(MethodIgnoredBy.class)) {
                    unusableBy = method.getAnnotation(MethodIgnoredBy.class).value();

                    if (unusableBy.length == 0) {
                        unusableBy = method.getAnnotation(MethodIgnoredBy.class).group().getDisguiseTypes();
                    }
                }

                String param = method.getParameterCount() == 1 ? method.getParameterTypes()[0].getName() : null;

                WatcherInfo info = new WatcherInfo();
                info.setMethod(method.getName());
                info.setAdded(added);
                info.setRemoved(removed);
                info.setUnusableBy(unusableBy);
                info.setDeprecated(method.isAnnotationPresent(Deprecated.class));
                info.setParam(param);
                info.setDescriptor(getMethodDescriptor(method));
                info.setWatcher(method.getDeclaringClass().getSimpleName());
                info.setReturnType(method.getReturnType().getName());
                info.setRandomDefault(method.isAnnotationPresent(RandomDefaultValue.class));

                String s = new Gson().toJson(info);

                if (methods.contains(s)) {
                    continue;
                }

                methods.add(s);
            }
        }

        File methodsFile = new File("plugin/target/classes/ANTI_PIRACY_ENCRYPTION");

        try (FileOutputStream fos = new FileOutputStream(methodsFile)) {
            byte[] array = String.join("\n", methods).getBytes(StandardCharsets.UTF_8);

            for (int i = 0; i < array.length; i++) {
                array[i] = (byte) (Byte.MAX_VALUE - array[i]);
            }

            fos.write(array);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String getDescriptorForClass(final Class c) {
        if (c.isPrimitive()) {
            if (c == byte.class) {
                return "B";
            }
            if (c == char.class) {
                return "C";
            }
            if (c == double.class) {
                return "D";
            }
            if (c == float.class) {
                return "F";
            }
            if (c == int.class) {
                return "I";
            }
            if (c == long.class) {
                return "J";
            }
            if (c == short.class) {
                return "S";
            }
            if (c == boolean.class) {
                return "Z";
            }
            if (c == void.class) {
                return "V";
            }

            throw new RuntimeException("Unrecognized primitive " + c);
        }

        if (c.isArray()) {
            return c.getName().replace('.', '/');
        }

        return ('L' + c.getName() + ';').replace('.', '/');
    }

    static String getMethodDescriptor(Method m) {
        StringBuilder s = new StringBuilder("(");

        for (final Class c : (m.getParameterTypes())) {
            s.append(getDescriptorForClass(c));
        }

        return s.append(")") + getDescriptorForClass(m.getReturnType());
    }
}
