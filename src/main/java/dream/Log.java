package dream;

/** Console logging: info is debug-only; warnings and errors always print. */
public final class Log {

    public static boolean debugMode = false;

    private Log() { }

    public static void info(String message) {
        if (debugMode) {
            System.out.println("[dream] " + message);
        }
    }

    public static void warn(String message) {
        System.out.println("[dream] " + message);
    }

    public static void warn(String message, Throwable cause) {
        System.out.println("[dream] " + describe(message, cause));
        if (debugMode && cause != null) {
            cause.printStackTrace();
        }
    }

    public static void error(String message, Throwable cause) {
        System.err.println("[dream] " + describe(message, cause));
        if (debugMode && cause != null) {
            cause.printStackTrace();
        }
    }

    /** Includes the exception type even when its message is missing. */
    private static String describe(String message, Throwable cause) {
        if (cause == null) {
            return message;
        }

        String type = cause.getClass().getSimpleName();
        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) {
            return message + " (" + type + ")";
        }
        return message + " (" + type + ": " + detail + ")";
    }
}
