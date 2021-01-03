package eutros.runtimeobf.util;

import eutros.runtimeobf.function.ThrowingFunction;

import java.util.regex.Matcher;

public class RegexHelper {
    public static <T extends Throwable> String replaceAll(Matcher matcher,
                                                          ThrowingFunction<Matcher, String, T> replacer)
            throws T {
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(sb, replacer.apply(matcher));
        matcher.appendTail(sb);
        return sb.toString();
    }
}
