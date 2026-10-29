package com.arazgray.neftlix;

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
            
            file.delete();
            String result = body.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception ignored) {
            return null;
        }
    }

    static String describe(android.content.Context context, Throwable error) {
        if (error == null) return context.getString(R.string.crash_unknown);
        String message = error.getMessage();
        String name = error.getClass().getSimpleName();
        if (name == null || name.isEmpty()) name = context.getString(R.string.crash_error);
        if (message == null) message = "";
        message = message.trim();
        if (message.length() > 300) message = message.substring(0, 300);
        return message.isEmpty() ? name : name + ": " + message;
    }

    private static java.io.File logFile(android.content.Context context) {
        return new java.io.File(context.getFilesDir(), "last_crash.log");
    }
}
