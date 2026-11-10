// Java 8, ZionScreenFilter.java, Android overlay and host JVM tests.
package com.mducdz.fakeping.engine;

/** Bounded screen-coordinate filter shared by the native and Java ESP paths. */
public final class ZionScreenFilter {
    private static final int CAPACITY = 256;
    private static final long STALE_MS = 200;
    private static final double RESPONSE_MS = 18.0;

    private final boolean[] occupied = new boolean[CAPACITY];
    private final long[] keys = new long[CAPACITY];
    private final long[] times = new long[CAPACITY];
    private final float[] xs = new float[CAPACITY];
    private final float[] ys = new float[CAPACITY];
    private int width;
    private int height;

    /** Mutates only projected x/y; retains the raw depth and never invents entities. */
    public boolean apply(int entity_id, float height_offset, float[] point,
                         int view_width, int view_height, long now_ms) {
        if (point == null || point.length < 3 || view_width <= 0 || view_height <= 0
                || !finite(height_offset) || !finite(point[0]) || !finite(point[1])
                || !finite(point[2])) {
            return false;
        }
        if (view_width != width || view_height != height) {
            for (int i = 0; i < CAPACITY; ++i) occupied[i] = false;
            width = view_width;
            height = view_height;
        }
        // Foot and head projections need independent histories for the same entity.
        long key = ((long) entity_id << 32) | (Float.floatToIntBits(height_offset) & 0xffffffffL);
        int home = ((int) (key ^ (key >>> 32)) * 0x9e3779b9) & (CAPACITY - 1);
        int slot = home;
        int reusable = -1;
        boolean found = false;
        for (int n = 0; n < CAPACITY; ++n) {
            if (!occupied[slot]) break;
            if (keys[slot] == key) {
                found = true;
                break;
            }
            long idle = now_ms - times[slot];
            if (reusable < 0 && (idle < 0 || idle >= STALE_MS)) reusable = slot;
            slot = (slot + 1) & (CAPACITY - 1);
        }
        if (!found) {
            if (reusable >= 0) slot = reusable;
            // A full cache overwrites one slot instead of growing or looping forever.
            occupied[slot] = true;
            keys[slot] = key;
            set(slot, point[0], point[1], now_ms);
            return true;
        }

        long delta = now_ms - times[slot];
        float dx = point[0] - xs[slot];
        float dy = point[1] - ys[slot];
        double snap = Math.max(48.0, Math.min(width, height) * 0.12);
        if (delta < 0 || delta >= STALE_MS
                || (double) dx * dx + (double) dy * dy > snap * snap) {
            set(slot, point[0], point[1], now_ms);
            return true;
        }
        if (delta == 0) {
            point[0] = xs[slot];
            point[1] = ys[slot];
            return true;
        }

        // Time-based response avoids tying the smoothing strength to display refresh rate.
        float alpha = (float) -Math.expm1(-delta / RESPONSE_MS);
        point[0] = xs[slot] + alpha * dx;
        point[1] = ys[slot] + alpha * dy;
        set(slot, point[0], point[1], now_ms);
        return true;
    }

    private void set(int slot, float x, float y, long now_ms) {
        xs[slot] = x;
        ys[slot] = y;
        times[slot] = now_ms;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
