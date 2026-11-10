// Java 8, ZionScreenFilterTest.java, host JVM; synthetic coordinates only.
import com.mducdz.fakeping.engine.ZionScreenFilter;

public final class ZionScreenFilterTest {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static float[] point(float x, float y) {
        return new float[] {x, y, 7.0f};
    }

    private static float response(int hz) {
        ZionScreenFilter filter = new ZionScreenFilter();
        filter.apply(1, 0, point(100, 100), 1080, 1920, 0);
        float[] value = null;
        for (int i = 1; i <= hz / 5; ++i) {
            value = point(120, 100);
            filter.apply(1, 0, value, 1080, 1920, Math.round(i * 1000.0 / hz));
        }
        return value[0];
    }

    public static void main(String[] args) {
        ZionScreenFilter filter = new ZionScreenFilter();
        require(!filter.apply(1, 0, null, 1080, 1920, 0), "null input");
        require(!filter.apply(1, 0, new float[2], 1080, 1920, 0), "short input");
        require(!filter.apply(1, 0, point(1, 1), 0, 1920, 0), "invalid viewport");
        require(!filter.apply(1, Float.NaN, point(1, 1), 1080, 1920, 0), "invalid offset");
        for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            for (int coordinate = 0; coordinate < 3; ++coordinate) {
                float[] p = point(100, 200);
                p[coordinate] = invalid;
                require(!filter.apply(1, 0, p, 1080, 1920, 0), "nonfinite coordinate");
            }
        }
        float[] p = point(100, 200);
        require(filter.apply(1, 0, p, 1080, 1920, 1000), "first sample");
        require(p[0] == 100 && p[1] == 200 && p[2] == 7, "initial/depth unchanged");
        p = point(120, 220);
        filter.apply(1, 0, p, 1080, 1920, 1016);
        require(p[0] > 100 && p[0] < 120 && p[1] > 200 && p[1] < 220, "smooth step");
        float previous_x = p[0];
        p = point(121, 221);
        filter.apply(1, 0, p, 1080, 1920, 1016);
        require(p[0] == previous_x, "same timestamp");
        p = point(400, 900);
        filter.apply(1, 1.8f, p, 1080, 1920, 1016);
        require(p[0] == 400 && p[1] == 900, "head/foot independent");
        p = point(800, 800);
        filter.apply(2, 0, p, 1080, 1920, 1016);
        require(p[0] == 800, "entity histories independent");
        p = point(900, 1000);
        filter.apply(1, 0, p, 1080, 1920, 1032);
        require(p[0] == 900 && p[1] == 1000, "camera cut snaps");
        p = point(910, 1000);
        filter.apply(1, 0, p, 1080, 1920, 1232);
        require(p[0] == 910, "stale history expires at 200 ms");
        p = point(915, 1000);
        filter.apply(1, 0, p, 1080, 1920, 1200);
        require(p[0] == 915, "clock rollback resets");
        p = point(920, 1000);
        filter.apply(1, 0, p, 1920, 1080, 1216);
        require(p[0] == 920, "rotation resets history");
        p = point(50, 50);
        filter.apply(1, -0.0f, p, 1920, 1080, 1232);
        require(p[0] == 50, "offset key preserves float bits");
        require(Math.abs(response(30) - response(60)) < 0.001f, "30/60 Hz response");
        require(Math.abs(response(60) - response(120)) < 0.001f, "60/120 Hz response");
        ZionScreenFilter crowded = new ZionScreenFilter();
        for (int i = 0; i < 2000; ++i) {
            p = point(i % 100, 100);
            require(crowded.apply(i, 0, p, 1080, 1920, i), "bounded cache handles churn");
        }
        // A stationary target with alternating 12 px noise should become less jittery.
        ZionScreenFilter noisy = new ZionScreenFilter();
        double raw_energy = 0, filtered_energy = 0;
        for (int i = 0; i < 300; ++i) {
            float raw = 500 + (i % 2 == 0 ? 12 : -12);
            p = point(raw, 500);
            noisy.apply(1, 0, p, 1080, 1920, i * 16);
            if (i > 30) {
                raw_energy += (raw - 500) * (raw - 500);
                filtered_energy += (p[0] - 500) * (p[0] - 500);
            }
        }
        require(filtered_energy < raw_energy * 0.5, "synthetic jitter reduced");
        System.out.printf("%d checks passed; synthetic jitter RMS ratio %.3f. No game/device test.%n",
                checks, Math.sqrt(filtered_energy / raw_energy));
    }
}
