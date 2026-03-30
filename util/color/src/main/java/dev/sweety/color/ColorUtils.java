package dev.sweety.color;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    public static String color(String message) {
        if (message.isEmpty()) return "";

        message = message.replace('&', '§');

        final char colorChar = '§';
        final Matcher matcher = Pattern.compile("§#([A-Fa-f\\d]{6})").matcher(message);
        final StringBuilder buffer = new StringBuilder(message.length() + 4 * 8);
        while (matcher.find()) {
            final String group = matcher.group(1);
            matcher.appendReplacement(buffer, colorChar + "x"
                    + colorChar + group.charAt(0) + colorChar + group.charAt(1)
                    + colorChar + group.charAt(2) + colorChar + group.charAt(3)
                    + colorChar + group.charAt(4) + colorChar + group.charAt(5));
        }
        return matcher.appendTail(buffer).toString();
    }


    public static List<String> color(List<String> list) {
        if (list == null || list.isEmpty()) return new ArrayList<>();
        List<String> colorList = new ArrayList<>();
        for (String string : list) {
            colorList.add(color(string));
        }
        return colorList;
    }
}
