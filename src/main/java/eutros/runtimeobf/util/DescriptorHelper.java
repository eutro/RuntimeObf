package eutros.runtimeobf.util;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DescriptorHelper {
    public static final Pattern DESCRIPTOR_NAME_PATTERN = Pattern.compile("L(.+?);");

    public static String maskArray(String internalName) {
        int dimensions = arrayDimensions(internalName);
        return dimensions == 0 ? internalName : internalName.substring(dimensions + 1, internalName.length() - 1);
    }

    public static String unmaskArray(String oldInternalName, String masked) {
        int dimensions = arrayDimensions(oldInternalName);
        return dimensions == 0 ? masked : repeatChar('[', dimensions) + "L" + masked + ";";
    }

    public static String eraseType(String internalName) {
        return internalName.replace(maskArray(internalName), "java/lang/Object");
    }

    public static String eraseDescriptorTypes(String descriptor, Predicate<String> internalNamePredicate) {
        return RegexHelper.replaceAll(DESCRIPTOR_NAME_PATTERN.matcher(descriptor),
                matcher -> internalNamePredicate.test(matcher.group(1)) ? "Ljava/lang/Object;" : "$0");
    }

    public static int arrayDimensions(String internalName) {
        return internalName.lastIndexOf('[') + 1;
    }

    public static String repeatChar(char c, int times) {
        char[] arr = new char[times];
        Arrays.fill(arr, c);
        return new String(arr);
    }
}
