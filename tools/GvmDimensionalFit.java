import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.*;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.apache.commons.math3.exception.TooManyEvaluationsException;

/** GVM-05 isolated fitting experiment. Not a Figaro library API or sampler. */
public final class GvmDimensionalFit {
    static final double TAU=2*Math.PI;
    static final class Limit extends RuntimeException { Limit(String reason){super(reason);} }
    static final class Budget {
        long phasePoints, densityPoints, calls;
        final long maxPoints, deadline;
        Budget(long maxPoints,double seconds){this.maxPoints=maxPoints;deadline=System.nanoTime()+(long)(seconds*1e9);}
        void check(){if(Thread.currentThread().isInterrupted())throw new Limit("Interrupted");if(System.nanoTime()>deadline)throw new Limit("TimeBudgetExhausted");}
        void phase(int n){check();if(phasePoints+n>maxPoints)throw new Limit("PhaseBudgetExhausted");phasePoints+=n;calls++;}
    }
    static double[] bessel(double k) {
        // Positive series, bounded to k <= 50; independently checked against SciPy/mpmath.
        double term0=1,sum0=1,term1=k/2,sum1=term1;
        for(int j=1;j<300;j++){
            term0*=k*k/(4*j*j);term1*=k*k/(4*j*(j+1));sum0+=term0;sum1+=term1;
            if(term0<=1e-16*sum0 && term1<=1e-16*Math.max(sum1,1e-300))break;
        }
        return new double[]{Math.log(sum0),sum1/sum0};
    }
    static double concentration(double r){
        if(r<=1e-14)return 0;if(r>=bessel(50)[1])return 50;
        double a=0,b=50;for(int j=0;j<55;j++){double m=(a+b)/2;if(bessel(m)[1]<r)a=m;else b=m;}return(a+b)/2;
    }
    static double[][] features(double[][] data,int d){
        double[][] f=new double[data.length][d+d*(d+1)/2];
        for(int n=0;n<data.length;n++){System.arraycopy(data[n],0,f[n],0,d);int q=d;
            for(int i=0;i<d;i++)for(int j=i;j<d;j++)f[n][q++]=(i==j?.5:1)*data[n][i]*data[n][j];}
        return f;
    }
    static double dot(double[] a,double[] b){double s=0;for(int i=0;i<a.length;i++)s+=a[i]*b[i];return s;}
    static final class Component {
        double weight,alpha,kappa,logI0,logNorm;
        double[] mean,coeff;double[][] cov,lower;
        int boundHits;
        Component(int d,int q){mean=new double[d];coeff=new double[q];cov=new double[d][d];}
        void factor(){
            lower=new CholeskyDecomposition(new Array2DRowRealMatrix(cov),1e-10,1e-12).getL().getData();
            logNorm=-mean.length*.5*Math.log(TAU);for(int i=0;i<mean.length;i++)logNorm-=Math.log(lower[i][i]);
            logI0=bessel(kappa)[0];
        }
        double log(double[] point,double[] f){
            double square=0;double[] z=new double[mean.length];
            for(int i=0;i<mean.length;i++){z[i]=point[i]-mean[i];for(int j=0;j<i;j++)z[i]-=lower[i][j]*z[j];z[i]/=lower[i][i];square+=z[i]*z[i];}
            return Math.log(weight)+logNorm-.5*square+kappa*Math.cos(point[mean.length]-alpha-dot(coeff,f))-Math.log(TAU)-logI0;
        }
    }
    static Component moments(double[][] x,double[] r,int d,int q){
        double mass=0,square=0;for(double w:r){mass+=w;square+=w*w;}
        if(mass<Math.max(30,5*d) || mass*mass/square<Math.max(30,5*d))throw new Limit("InsufficientComponent");
        Component c=new Component(d,q);c.weight=mass/x.length;
        for(int n=0;n<x.length;n++)for(int i=0;i<d;i++)c.mean[i]+=r[n]*x[n][i]/mass;
        for(int n=0;n<x.length;n++)for(int i=0;i<d;i++)for(int j=0;j<d;j++)c.cov[i][j]+=r[n]*(x[n][i]-c.mean[i])*(x[n][j]-c.mean[j])/mass;
        var eig=new EigenDecomposition(new Array2DRowRealMatrix(c.cov));double[] values=eig.getRealEigenvalues();
        for(int i=0;i<d;i++){if(values[i]<.01||values[i]>25)c.boundHits++;values[i]=Math.max(.01,Math.min(25,values[i]));}
        // Spectral reconstruction is mathematically symmetric. Compute each
        // pair once: independent matrix products can leave asymmetric roundoff
        // near zero, which the strict relative Cholesky check correctly refuses.
        double[][] vectors=eig.getV().getData();
        for(int i=0;i<d;i++)for(int j=i;j<d;j++){
            double value=0;for(int a=0;a<d;a++)value+=vectors[i][a]*values[a]*vectors[j][a];
            c.cov[i][j]=c.cov[j][i]=value;
        }
        return c;
    }
    static double[] resultant(double[][] x,double[][] f,double[] r,double[] coeff){
        double a=0,b=0,mass=0;int d=x[0].length-1;
        for(int n=0;n<x.length;n++){double angle=x[n][d]-dot(f[n],coeff);a+=r[n]*Math.cos(angle);b+=r[n]*Math.sin(angle);mass+=r[n];}
        return new double[]{a/mass,b/mass};
    }
    static final class Angular {
        boolean exhausted;double[] best;double value=Double.POSITIVE_INFINITY;
        double evaluate(double[] coeff,double[][] x,double[][] f,double[] r,Budget budget){
            budget.phase(x.length);double[] z=resultant(x,f,r,coeff);double v=-dot(z,z);
            if(v<value){value=v;best=coeff.clone();}return v;
        }
    }
    static Angular angular(Component c,double[] initial,double[][] x,double[][] f,double[] r,Budget budget,int maxEval){
        Angular state=new Angular();state.evaluate(initial,x,f,r,budget);
        double[] low=new double[initial.length],high=new double[initial.length];Arrays.fill(low,-4);Arrays.fill(high,4);
        try{
            new BOBYQAOptimizer(2*initial.length+1,.3,1e-6).optimize(new MaxEval(maxEval),
                new ObjectiveFunction(v->state.evaluate(v,x,f,r,budget)),GoalType.MINIMIZE,
                new InitialGuess(initial),new SimpleBounds(low,high));
        }catch(TooManyEvaluationsException e){state.exhausted=true;}
        c.coeff=state.best;budget.phase(x.length);double[] z=resultant(x,f,r,c.coeff);
        c.alpha=Math.atan2(z[1],z[0]);c.kappa=concentration(Math.min(1,Math.hypot(z[0],z[1])));c.factor();return state;
    }
    static double score(Component[] c,double[][] x,double[][] f,double[][] responsibilities,Budget budget){
        budget.check();budget.densityPoints+=(long)x.length*c.length;double total=0;
        for(int n=0;n<x.length;n++){
            double[] logs=new double[c.length];double max=Double.NEGATIVE_INFINITY,sum=0;
            for(int j=0;j<c.length;j++){logs[j]=c[j].log(x[n],f[n]);max=Math.max(max,logs[j]);}
            for(double log:logs)sum+=Math.exp(log-max);double normalizer=max+Math.log(sum);total+=normalizer;
            if(responsibilities!=null)for(int j=0;j<c.length;j++)responsibilities[j][n]=Math.exp(logs[j]-normalizer);
        }
        if(!Double.isFinite(total))throw new Limit("NumericallyUnresolved");return total;
    }
    static String vector(double[] x){return Arrays.toString(x);}
    static String matrix(double[][] x){return Arrays.deepToString(x);}
    static String model(Component[] components){
        if(components==null)return "[]";List<String> out=new ArrayList<>();
        for(Component c:components)out.add("{\"weight\":"+c.weight+",\"mean\":"+vector(c.mean)+",\"cov\":"+matrix(c.cov)+",\"alpha\":"+c.alpha+",\"coeff\":"+vector(c.coeff)+",\"kappa\":"+c.kappa+"}");
        return "["+String.join(",",out)+"]";
    }
    static String fit(double[][] x,int k,long seed,int maxEval,int maxIter,long maxPoints,double seconds){
        long begun=System.nanoTime();int n=x.length,d=x[0].length-1,q=d+d*(d+1)/2;
        if(n<30||n>5000||d<1||d>3||k<1||k>3||maxEval<2*q+2||maxIter<1)throw new IllegalArgumentException("Invalid data/configuration");
        for(double[] point:x){if(point.length!=d+1)throw new IllegalArgumentException("Ragged input");for(double v:point)if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite input");}
        double[] all=new double[n];Arrays.fill(all,1.);Component global=moments(x,all,d,q);
        // Rank is checked before a covariance floor can conceal the input defect.
        double[][] raw=new double[d][d];for(double[] row:x)for(int i=0;i<d;i++)for(int j=0;j<d;j++)raw[i][j]+=(row[i]-global.mean[i])*(row[j]-global.mean[j])/n;
        if(Arrays.stream(new EigenDecomposition(new Array2DRowRealMatrix(raw)).getRealEigenvalues()).min().orElse(0)<=1e-10)
            return "{\"status\":\"RankDeficient\",\"components\":[],\"starts\":[],\"phasePoints\":0,\"densityPoints\":0,\"seconds\":0}";
        double[][] f=features(x,d);Budget budget=new Budget(maxPoints,seconds);
        Component[] best=null;double bestScore=Double.NEGATIVE_INFINITY;String bestStatus="NumericallyUnresolved";
        List<String> starts=new ArrayList<>();String globalFailure=null;
        for(int start=0;start<3;start++){
            Component[] current=null;List<Double> trace=new ArrayList<>();String status="IterationLimit",error="";int innerCaps=0,hits=0;
            try{
                double[][] r=new double[k][n];Integer[] order=new Integer[n];double[] keys=new double[n];var rng=new SplittableRandom(seed+start);
                for(int i=0;i<n;i++){order[i]=i;keys[i]=start==0?x[i][0]:start==1?Math.atan2(Math.sin(x[i][d]),Math.cos(x[i][d])):rng.nextDouble();}
                Arrays.sort(order,Comparator.comparingDouble(i->keys[i]));for(int i=0;i<n;i++)r[Math.min(k-1,i*k/n)][order[i]]=1;
                current=new Component[k];
                for(int j=0;j<k;j++){current[j]=moments(x,r[j],d,q);budget.phase(n);double[] z=resultant(x,f,r[j],new double[q]);current[j].alpha=Math.atan2(z[1],z[0]);current[j].kappa=concentration(Math.min(1,Math.hypot(z[0],z[1])));current[j].factor();}
                trace.add(score(current,x,f,null,budget));
                for(int iteration=0;iteration<maxIter;iteration++){
                    score(current,x,f,r,budget);Component[] next=new Component[k];boolean capped=false;
                    for(int j=0;j<k;j++){next[j]=moments(x,r[j],d,q);hits+=next[j].boundHits;
                        Angular a=angular(next[j],current[j].coeff,x,f,r[j],budget,maxEval);if(a.exhausted){innerCaps++;capped=true;}}
                    double value=score(next,x,f,null,budget),previous=trace.get(trace.size()-1);
                    if(value<previous-1e-8*n){status="Stalled";break;}
                    current=next;trace.add(value);
                    if((value-previous)/n<=1e-5){status=capped?"AngularBudgetExhausted":"Converged";break;}
                }
            }catch(Limit e){status=e.getMessage();if(status.equals("PhaseBudgetExhausted")||status.equals("TimeBudgetExhausted")||status.equals("Interrupted"))globalFailure=status;
            }catch(RuntimeException e){status="NumericallyUnresolved";error=e.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ");}
            double value=trace.isEmpty()?Double.NEGATIVE_INFINITY:trace.get(trace.size()-1);
            starts.add("{\"start\":"+start+",\"status\":\""+status+"\",\"trace\":"+trace+",\"innerBudgetHits\":"+innerCaps+",\"covarianceBoundHits\":"+hits+",\"error\":\""+error+"\"}");
            if(best==null)bestStatus=status;
            if(value>bestScore){bestScore=value;best=current;bestStatus=status;}
            if(globalFailure!=null)break;
        }
        if(globalFailure!=null)bestStatus=globalFailure;
        return "{\"status\":\""+bestStatus+"\",\"components\":"+model(best)+",\"trainScore\":"+(Double.isFinite(bestScore)?bestScore:"null")+
            ",\"starts\":["+String.join(",",starts)+"],\"phasePoints\":"+budget.phasePoints+",\"densityPoints\":"+budget.densityPoints+",\"angularCalls\":"+budget.calls+",\"seconds\":"+(System.nanoTime()-begun)/1e9+"}";
    }
    public static void main(String[] args)throws Exception{
        Locale.setDefault(Locale.ROOT);
        if(args.length==1&&args[0].equals("bessel")){for(double k:new double[]{0,1e-8,.1,1,8,25,50})System.out.println(Arrays.toString(new double[]{k,bessel(k)[0],bessel(k)[1]}));return;}
        List<double[]> rows=new ArrayList<>();for(String line:Files.readAllLines(Path.of(args[0]))){if(line.isBlank())continue;String[] cells=line.split("\\s+");double[] row=new double[cells.length];for(int j=0;j<row.length;j++)row[j]=Double.parseDouble(cells[j]);rows.add(row);}
        System.out.println(fit(rows.toArray(double[][]::new),Integer.parseInt(args[1]),Long.parseLong(args[2]),
            args.length>3?Integer.parseInt(args[3]):400,args.length>4?Integer.parseInt(args[4]):50,
            args.length>5?Long.parseLong(args[5]):20000000,args.length>6?Double.parseDouble(args[6]):120));
    }
}
