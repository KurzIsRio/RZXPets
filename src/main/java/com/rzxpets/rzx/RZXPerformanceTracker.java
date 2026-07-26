package com.rzxpets.rzx;

public class RZXPerformanceTracker {
    private static final long WARN_THRESHOLD_MS = 50L;

    public static void track(String operationName, Runnable runnable) {
        long start = System.currentTimeMillis();
        try {
            runnable.run();
        } finally {
            long duration = System.currentTimeMillis() - start;
            if (duration > WARN_THRESHOLD_MS) {
                RZXLoggerService.warning(String.format("Performance Warning: %s took %dms (Threshold: %dms)", operationName, duration, WARN_THRESHOLD_MS));
            }
        }
    }

    public static <T> T track(String operationName, java.util.function.Supplier<T> supplier) {
        long start = System.currentTimeMillis();
        try {
            return supplier.get();
        } finally {
            long duration = System.currentTimeMillis() - start;
            if (duration > WARN_THRESHOLD_MS) {
                RZXLoggerService.warning(String.format("Performance Warning: %s took %dms (Threshold: %dms)", operationName, duration, WARN_THRESHOLD_MS));
            }
        }
    }
}
