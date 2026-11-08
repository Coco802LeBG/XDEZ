package net.krypton.utility;

/**
 * The only logging entry point of the mod. It never prints exception messages, file paths, player names, coordinates
 * or server addresses: only a fixed text, the exception class and the mod frame where it happened.
 */
public final class SafeLog {
    private SafeLog() {
    }

    public static void info(String fixedMessage) {
        KryptonUtility.LOGGER.info(fixedMessage);
    }

    public static void warn(String fixedMessage, Throwable error) {
        KryptonUtility.LOGGER.warn("{} [{} at {}]", fixedMessage, error.getClass().getName(), originOf(error));
    }

    private static String originOf(Throwable error) {
        StackTraceElement[] frames = error.getStackTrace();
        for (StackTraceElement f : frames) {
            if (f.getClassName().startsWith("net.krypton.utility")) {
                return f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber();
            }
        }
        return frames.length == 0 ? "unknown" : frames[0].getClassName() + "." + frames[0].getMethodName();
    }
}
