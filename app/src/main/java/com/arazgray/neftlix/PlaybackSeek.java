package com.arazgray.neftlix;

final class PlaybackSeek {
    static final long BACK_MS = 5000;
    static final long FORWARD_MS = 15000;

    static long target(long position, long duration, boolean forward) {
        long target = Math.max(0, position + (forward ? FORWARD_MS : -BACK_MS));
        return duration >= 0 ? Math.min(duration, target) : target;
    }
}
