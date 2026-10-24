import java.util.Arrays;
import java.util.SplittableRandom;

/** Test-only initialization bridge: reuse the frozen baseline's third-start keys. */
public final class GvmGradientKeys {
    public static void main(String[] args) {
        int n = Integer.parseInt(args[0]);
        if (n < 30 || n > 5000) throw new IllegalArgumentException("Invalid size");
        SplittableRandom rng = new SplittableRandom(Long.parseLong(args[1]) + 2);
        double[] keys = new double[n];
        for (int i = 0; i < n; i++) keys[i] = rng.nextDouble();
        System.out.println(Arrays.toString(keys));
    }
}
