import java.util.Locale;
import org.apache.commons.math3.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math3.ode.AbstractIntegrator;
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator;
import org.apache.commons.math3.ode.nonstiff.GraggBulirschStoerIntegrator;

/** INF-19 model-only screen. No Figaro filtering or proposal code is invoked. */
public final class ExpensiveLikelihoodRelevance {
    private static volatile double sink;
    private static int checks, failures;
    private record Result(double value, double derivative, int rhs) {}

    private static Result solve(double x, double end, boolean tangent,
                                boolean linear, boolean gbs, double tolerance) {
        AbstractIntegrator solver = gbs
            ? new GraggBulirschStoerIntegrator(1e-10, .5, tolerance, tolerance)
            : new DormandPrince853Integrator(1e-10, .5, tolerance, tolerance);
        solver.setMaxEvaluations(100000);
        FirstOrderDifferentialEquations equations = new FirstOrderDifferentialEquations() {
            public int getDimension() { return tangent ? 4 : 2; }
            public void computeDerivatives(double time, double[] z, double[] dz) {
                if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Interrupted");
                dz[0] = z[1];
                dz[1] = -(linear ? z[0] : Math.sin(z[0])) - .15 * z[1];
                if (tangent) {
                    dz[2] = z[3];
                    dz[3] = -(linear ? 1 : Math.cos(z[0])) * z[2] - .15 * z[3];
                }
            }
        };
        double[] initial = tangent ? new double[]{.2, x, 0, 1} : new double[]{.2, x};
        double[] out = initial.clone();
        solver.integrate(equations, 0, initial, end, out);
        for (double value : out) if (!Double.isFinite(value)) throw new ArithmeticException("Nonfinite solution");
        return new Result(out[0], tangent ? out[2] : 0, solver.getEvaluations());
    }

    private static double linear(double x, double end) {
        double omega = Math.sqrt(1 - .075 * .075);
        return Math.exp(-.075 * end) * (.2 * Math.cos(omega * end)
            + (x + .075 * .2) / omega * Math.sin(omega * end));
    }

    private static void check(String name, double end, double x, double error, double limit) {
        checks++;
        if (!Double.isFinite(error) || error > limit) {
            failures++;
            System.out.printf("{\"type\":\"failedCheck\",\"name\":\"%s\",\"T\":%.1f,\"x\":%.17g,\"error\":%.17g,\"limit\":%.17g}%n",
                name, end, x, error, limit);
        }
    }

    private static void validate() {
        for (double end : new double[]{2, 6}) for (int i = 0; i <= 320; i++) {
            double x = -8 + i * .05;
            Result loose = solve(x, end, false, false, false, 1e-10);
            Result alternate = solve(x, end, false, false, true, 1e-10);
            Result tight = solve(x, end, false, false, false, 1e-11);
            Result other = solve(x, end, false, false, true, 1e-11);
            Result tangent = solve(x, end, true, false, false, 1e-10);
            Result alternateTangent = solve(x, end, true, false, true, 1e-10);
            double delta = 1e-4;
            double fd = (solve(x + delta, end, false, false, false, 1e-11).value
                       - solve(x - delta, end, false, false, false, 1e-11).value) / (2 * delta);
            double fdOther = (solve(x + delta, end, false, false, true, 1e-11).value
                            - solve(x - delta, end, false, false, true, 1e-11).value) / (2 * delta);
            double refinement = Math.abs(loose.value - tight.value);
            double cross = Math.abs(loose.value - other.value);
            double aug = Math.abs(loose.value - tangent.value);
            double diff = Math.abs(tangent.derivative - fd);
            double diffOther = Math.abs(tangent.derivative - fdOther);
            double analytic = Math.abs(solve(x, end, false, true, false, 1e-10).value - linear(x, end));
            check("alternate-endpoint-dp", end, x, Math.abs(alternate.value-tight.value), 1e-6);
            check("alternate-endpoint-gbs", end, x, Math.abs(alternate.value-other.value), 1e-6);
            check("alternate-augmented", end, x, Math.abs(alternate.value-alternateTangent.value), 1e-6);
            check("alternate-derivative-dp", end, x, Math.abs(alternateTangent.derivative-fd), 1e-4*Math.max(1,Math.abs(fd)));
            check("alternate-derivative-gbs", end, x, Math.abs(alternateTangent.derivative-fdOther), 1e-4*Math.max(1,Math.abs(fdOther)));
            check("alternate-linear", end, x, Math.abs(solve(x,end,false,true,true,1e-10).value-linear(x,end)), 1e-6);
            check("refinement", end, x, refinement, 1e-6);
            check("distinct-solver", end, x, cross, 1e-6);
            check("augmented-endpoint", end, x, aug, 1e-6);
            check("derivative-dp", end, x, diff, 1e-4 * Math.max(1, Math.abs(fd)));
            check("derivative-gbs", end, x, diffOther, 1e-4 * Math.max(1, Math.abs(fdOther)));
            check("linear-analytic", end, x, analytic, 1e-6);
            System.out.printf("{\"type\":\"point\",\"T\":%.1f,\"x\":%.17g,\"h\":%.17g,\"derivative\":%.17g,\"rhs\":%d,\"tangentRhs\":%d,\"refinementError\":%.17g,\"crossError\":%.17g,\"augmentedError\":%.17g,\"derivativeError\":%.17g,\"otherDerivativeError\":%.17g,\"linearError\":%.17g}%n",
                end, x, loose.value, tangent.derivative, loose.rhs, tangent.rhs,
                refinement, cross, aug, diff, diffOther, analytic);
        }
        System.out.printf("{\"type\":\"checks\",\"checks\":%d,\"failures\":%d}%n", checks, failures);
        if (failures != 0) throw new AssertionError("Numerical relevance checks failed: " + failures);
    }

    private static void batch(double end, boolean tangent, boolean gbs, int batch) {
        long rhs = 0;
        double sum = 0;
        long start = System.nanoTime();
        for (int i = 0; i <= 320; i++) {
            Result r = solve(-8 + i * .05, end, tangent, false, gbs, 1e-10);
            sum += r.value + r.derivative;
            rhs += r.rhs;
        }
        long elapsed = System.nanoTime() - start;
        sink = sum;
        if (batch >= 0) System.out.printf("{\"type\":\"timing\",\"T\":%.1f,\"tangent\":%s,\"gbs\":%s,\"batch\":%d,\"calls\":321,\"rhs\":%d,\"ns\":%d,\"checksum\":%.17g}%n",
            end, tangent, gbs, batch, rhs, elapsed, sink);
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("{\"type\":\"environment\",\"java\":\"%s\",\"vm\":\"%s\",\"os\":\"%s\",\"arch\":\"%s\",\"processors\":%d}%n",
            System.getProperty("java.version"), System.getProperty("java.vm.name"),
            System.getProperty("os.name"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors());
        validate();
        for (int batch = -3; batch < 7; batch++) for (double end : new double[]{2, 6}) {
            boolean first = batch % 2 == 0;
            batch(end, first, first, batch);
            batch(end, !first, first, batch);
            batch(end, !first, !first, batch);
            batch(end, first, !first, batch);
        }
        System.out.println("{\"type\":\"complete\"}");
    }
}
