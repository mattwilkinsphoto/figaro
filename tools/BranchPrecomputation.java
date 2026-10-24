import java.util.Locale;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator;

/** INF-19 isolated forward-model comparison; no Figaro inference is invoked. */
public final class BranchPrecomputation {
    private static volatile double sink;
    static double direct(double x, double end) {
        if (!Double.isFinite(x)) throw new IllegalArgumentException("Nonfinite input");
        var solver = new DormandPrince853Integrator(1e-10,.5,1e-10,1e-10);
        solver.setMaxEvaluations(100000);
        var equation = new FirstOrderDifferentialEquations() {
            public int getDimension() { return 2; }
            public void computeDerivatives(double t,double[] y,double[] d) {
                if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Interrupted");
                d[0]=y[1]; d[1]=-Math.sin(y[0])-.15*y[1];
            }
        };
        double[] initial={.2,x}, out=new double[2];
        solver.integrate(equation,0,initial,end,out);
        if (!Double.isFinite(out[0])) throw new ArithmeticException("Nonfinite response");
        return out[0];
    }
    static final class Table {
        final double end, spacing;
        final double[] x,y;
        final PolynomialSplineFunction spline;
        final long constructionNs;
        long fallbacks;
        Table(double end,double spacing) {
            long start=System.nanoTime();
            this.end=end; this.spacing=spacing;
            int n=(int)Math.round(20/spacing)+1;
            x=new double[n];y=new double[n];
            for(int i=0;i<n;i++){x[i]=-10+i*spacing;y[i]=direct(x[i],end);}
            spline=new SplineInterpolator().interpolate(x,y);
            constructionNs=System.nanoTime()-start;
        }
        double value(double v) {
            if (!Double.isFinite(v)) throw new IllegalArgumentException("Nonfinite input");
            if(v < -8 || v > 8){fallbacks++;return direct(v,end);}
            return spline.value(v);
        }
    }
    static void batch(double end,Table[] tables,int b,boolean prior) {
        double[] query=new double[1024];
        var normal=new NormalDistribution(0,1.1);
        for(int i=0;i<query.length;i++) query[i]=prior?normal.inverseCumulativeProbability((i+.5)/1024): -8+(i+.5)*16/1024;
        for(int order=0;order<5;order++){
            int method=b%2==0?order:4-order;
            long start=System.nanoTime();double sum=0;
            for(double x:query)sum+=method==0?direct(x,end):tables[method-1].value(x);
            long ns=System.nanoTime()-start;sink=sum;
            if(b>=0)System.out.printf("{\"type\":\"timing\",\"T\":%.1f,\"method\":%d,\"prior\":%s,\"batch\":%d,\"calls\":1024,\"ns\":%d,\"checksum\":%.17g}%n",end,method,prior,b,ns,sink);
        }
    }
    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("{\"type\":\"environment\",\"java\":\"%s\"}%n",System.getProperty("java.version"));
        for(double end:new double[]{2,6}){
            Table[] tables=new Table[4];double[] widths={.02,.01,.005,.0025};
            for(int k=0;k<4;k++){
                tables[k]=new Table(end,widths[k]);Table t=tables[k];
                System.out.printf("{\"type\":\"table\",\"T\":%.1f,\"method\":%d,\"spacing\":%.5f,\"nodes\":%d,\"constructionNs\":%d}%n",end,k+1,t.spacing,t.x.length,t.constructionNs);
                for(int i=0;i<t.x.length;i++)System.out.printf("{\"type\":\"node\",\"T\":%.1f,\"method\":%d,\"x\":%.17g,\"h\":%.17g}%n",end,k+1,t.x[i],t.y[i]);
                for(int set=0;set<2;set++){
                    int n=set==0?1024:4096;double offset=set==0?.37:.73;
                    for(int i=0;i<n;i++){
                        double x=-8+(i+offset)*16/n;
                        System.out.printf("{\"type\":\"query\",\"T\":%.1f,\"method\":%d,\"set\":%d,\"index\":%d,\"x\":%.17g,\"h\":%.17g}%n",end,k+1,set,i,x,t.value(x));
                    }
                }
                for(double x:new double[]{-12,-10,-8.0001,8.0001,10,12}){
                    double a=t.value(x),b=direct(x,end);
                    if(a!=b)throw new AssertionError("Fallback mismatch");
                }
                if(t.fallbacks!=6)throw new AssertionError("Fallback count");
                System.out.printf("{\"type\":\"fallback\",\"T\":%.1f,\"method\":%d,\"count\":%d}%n",end,k+1,t.fallbacks);
            }
            for(int b=-3;b<7;b++){batch(end,tables,b,false);batch(end,tables,b,true);}
        }
        System.out.println("{\"type\":\"complete\"}");
    }
}
