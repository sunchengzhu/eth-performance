import java.util.TimeZone;

public class Color {
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String CYAN = "\u001B[36m";

    public static void printColored(String message, String color) {
        // 设置时区为中国
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        System.out.println(color + message + Color.RESET);
    }
}
