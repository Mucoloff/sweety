package dev.sweety.core.color;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.awt.*;

@Getter
@AllArgsConstructor
public enum AnsiColor {
    // Reset
    RESET("\033[0m"),   // Text Reset

    // Regular Colors
    BLACK("\033[0;30m"),   // BLACK
    RED("\033[0;31m"),     // RED
    GREEN("\033[0;32m"),   // GREEN
    YELLOW("\033[0;33m"),  // YELLOW
    BLUE("\033[0;34m"),    // BLUE
    PURPLE("\033[0;35m"),  // PURPLE
    CYAN("\033[0;36m"),    // CYAN
    WHITE("\033[0;37m"),   // WHITE
    GRAY("\u001B[90m"),

    // Bold
    BLACK_BOLD("\033[1;30m"),  // BLACK
    RED_BOLD("\033[1;31m"),    // RED
    GREEN_BOLD("\033[1;32m"),  // GREEN
    YELLOW_BOLD("\033[1;33m"), // YELLOW
    BLUE_BOLD("\033[1;34m"),   // BLUE
    PURPLE_BOLD("\033[1;35m"), // PURPLE
    CYAN_BOLD("\033[1;36m"),   // CYAN
    WHITE_BOLD("\033[1;37m"),  // WHITE

    // Underline
    BLACK_UNDERLINED("\033[4;30m"),  // BLACK
    RED_UNDERLINED("\033[4;31m"),    // RED
    GREEN_UNDERLINED("\033[4;32m"),  // GREEN
    YELLOW_UNDERLINED("\033[4;33m"), // YELLOW
    BLUE_UNDERLINED("\033[4;34m"),   // BLUE
    PURPLE_UNDERLINED("\033[4;35m"), // PURPLE
    CYAN_UNDERLINED("\033[4;36m"),   // CYAN
    WHITE_UNDERLINED("\033[4;37m"),  // WHITE

    // Background
    BLACK_BACKGROUND("\033[40m"),  // BLACK
    RED_BACKGROUND("\033[41m"),    // RED
    GREEN_BACKGROUND("\033[42m"),  // GREEN
    YELLOW_BACKGROUND("\033[43m"), // YELLOW
    BLUE_BACKGROUND("\033[44m"),   // BLUE
    PURPLE_BACKGROUND("\033[45m"), // PURPLE
    CYAN_BACKGROUND("\033[46m"),   // CYAN
    WHITE_BACKGROUND("\033[47m"),  // WHITE

    // High Intensity
    BLACK_BRIGHT("\033[0;90m"),  // BLACK
    RED_BRIGHT("\033[0;91m"),    // RED
    GREEN_BRIGHT("\033[0;92m"),  // GREEN
    YELLOW_BRIGHT("\033[0;93m"), // YELLOW
    BLUE_BRIGHT("\033[0;94m"),   // BLUE
    PURPLE_BRIGHT("\033[0;95m"), // PURPLE
    CYAN_BRIGHT("\033[0;96m"),   // CYAN
    WHITE_BRIGHT("\033[0;97m"),  // WHITE
    GRAY_BRIGHT("\u001B[37m"),

    // Bold High Intensity
    BLACK_BOLD_BRIGHT("\033[1;90m"), // BLACK
    RED_BOLD_BRIGHT("\033[1;91m"),   // RED
    GREEN_BOLD_BRIGHT("\033[1;92m"), // GREEN
    YELLOW_BOLD_BRIGHT("\033[1;93m"),// YELLOW
    BLUE_BOLD_BRIGHT("\033[1;94m"),  // BLUE
    PURPLE_BOLD_BRIGHT("\033[1;95m"),// PURPLE
    CYAN_BOLD_BRIGHT("\033[1;96m"),  // CYAN
    WHITE_BOLD_BRIGHT("\033[1;97m"), // WHITE

    // High Intensity backgrounds
    BLACK_BACKGROUND_BRIGHT("\033[0;100m"),// BLACK
    RED_BACKGROUND_BRIGHT("\033[0;101m"),// RED
    GREEN_BACKGROUND_BRIGHT("\033[0;102m"),// GREEN
    YELLOW_BACKGROUND_BRIGHT("\033[0;103m"),// YELLOW
    BLUE_BACKGROUND_BRIGHT("\033[0;104m"),// BLUE
    PURPLE_BACKGROUND_BRIGHT("\033[0;105m"), // PURPLE
    CYAN_BACKGROUND_BRIGHT("\033[0;106m"),  // CYAN
    WHITE_BACKGROUND_BRIGHT("\033[0;107m"),   // WHITE
    ;

    public static final AnsiColor[] VALUES = values();

    final String color;

    public static String clear(String text) {
        for (AnsiColor value : values()) {
            if (text.contains(value.color)) text = text.replace(value.color, "");
        }
        return text;
    }

    public static String fromColor(EColor color) {
        return String.format("\u001B[38;2;%d;%d;%dm", color.getR(), color.getG(), color.getB());
    }

    public static String fromColor(Color color) {
        return String.format("\u001B[38;2;%d;%d;%dm", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static String fromColor(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return String.format("\u001B[38;2;%d;%d;%dm", red, green, blue);
    }

}