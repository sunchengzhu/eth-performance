package com.nervos.layer2;

public class Color {
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String CYAN = "\u001B[36m";

    public static void printColored(String message, String color) {
        System.out.println(color + message + Color.RESET);
    }
}
