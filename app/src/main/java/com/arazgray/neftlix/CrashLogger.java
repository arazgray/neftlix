package com.arazgray.neftlix;

/**
 * ذخیره رد خطای کرش در حافظه داخلی تا در اجرای بعد نمایش داده شود.
 * هندلر پیش‌فرض زنجیره می‌شود؛ رفتار خروج برنامه تغییر نمی‌کند.
 */
final class CrashLogger {
    private CrashLogger() {
    }

    static void install(android.content.Context context) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                java.io.PrintWriter writer =
                        new java.io.PrintWriter(new java.io.FileWriter(logFile(context), false));
                writer.println(error.toString());
                for (StackTraceElement element : error.getStackTrace()) {
                    writer.println("    at " + element.toString());
                }
                writer.close();
            } catch (Exception ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    /** خواندن رد خطا و پاک‌سازی آن؛ null یعنی کرشی ثبت نشده است. */
    static String consume(android.content.Context context) {
        java.io.File file = logFile(context);
        if (!file.exists()) return null;
        try {
            StringBuilder body = new StringBuilder();
            java.io.BufferedReader reader =
                    new java.io.BufferedReader(new java.io.FileReader(file));
            String line;
            int lines = 0;
            while ((line = reader.readLine()) != null && lines < 60) {
                body.append(line).append('\n');
                lines++;
            }
            reader.close();
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            String result = body.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static java.io.File logFile(android.content.Context context) {
        return new java.io.File(context.getFilesDir(), "last_crash.log");
    }
}
