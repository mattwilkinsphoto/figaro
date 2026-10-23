import java.lang.reflect.*;

/** Minimal upstream-review reproducer: actual sbt class, controlled Process only. */
public class WorkerCompletionCheck {
  static final String OK = "{\"id\":42,\"result\":0}";
  static final String ERROR = "{\"id\":42,\"error\":{\"code\":-1,\"message\":\"sentinel\"}}";
  public static void main(String[] args) throws Exception {
    boolean stock = args.length == 1 && args[0].equals("stock");
    if (args.length != 1 || !(stock || args[0].equals("repaired"))) throw new IllegalArgumentException("stock|repaired");
    Class<?> react = Class.forName("sbt.React"), processType = Class.forName("scala.sys.process.Process");
    System.out.println("BINARY " + react.getProtectionDomain().getCodeSource().getLocation());
    Constructor<?> ctor = react.getDeclaredConstructors()[0]; ctor.setAccessible(true);
    Method response = react.getDeclaredMethod("apply", String.class); response.setAccessible(true);
    Method exit = react.getDeclaredMethod("notifyExit", processType); exit.setAccessible(true);
    Method block = react.getDeclaredMethod("blockForResponse"); block.setAccessible(true);
    for (String mode : new String[]{"response-first","exit-first","interleaved-success","error-first",
                                    "interleaved-error","nonzero-first","duplicate-exit","wrong-id"}) {
      int exceptions=0;
      boolean failure = mode.equals("error-first") || mode.equals("interleaved-error") || mode.equals("nonzero-first");
      for (int i=0;i<20;i++) {
        Object[] holder=new Object[1];
        Object process=Proxy.newProxyInstance(processType.getClassLoader(),new Class<?>[]{processType},(p,m,a)->{
          if(m.getName().equals("isAlive")) return mode.equals("wrong-id");
          if(m.getName().equals("exitValue")) {
            if(mode.startsWith("interleaved")) response.invoke(holder[0],mode.endsWith("error")?ERROR:OK);
            return mode.equals("nonzero-first")?17:0;
          }
          throw new UnsupportedOperationException(m.toString());
        });
        holder[0]=ctor.newInstance(42L,null,null,null,process);
        if(mode.equals("response-first")) response.invoke(holder[0],OK);
        if(mode.equals("error-first")) response.invoke(holder[0],ERROR);
        if(mode.equals("wrong-id")) {
          response.invoke(holder[0],"{\"id\":17,\"result\":0}");
          Method pm=react.getDeclaredMethod("promise");pm.setAccessible(true);
          Object promise=pm.invoke(holder[0]);
          if((boolean)Class.forName("scala.concurrent.Promise").getMethod("isCompleted").invoke(promise))
            throw new AssertionError("Unrelated response completed request");
        }
        try { exit.invoke(holder[0],process); }
        catch(InvocationTargetException e) {
          if(!(e.getCause() instanceof IllegalStateException) || !"Promise already completed.".equals(e.getCause().getMessage())) throw e;
          exceptions++;
        }
        if(mode.equals("duplicate-exit")) exit.invoke(holder[0],process);
        // Late success must not replace an earlier failure; it must also be safe after success.
        response.invoke(holder[0],OK);
        try {
          Object result=block.invoke(holder[0]);
          if(failure || !Integer.valueOf(0).equals(result)) throw new AssertionError("Lost failure: "+mode);
        } catch(InvocationTargetException e) {
          if(!failure || !(e.getCause() instanceof RuntimeException)) throw e;
          String message=e.getCause().getMessage();
          if(!message.contains(mode.equals("nonzero-first")?"17":"sentinel")) throw e;
        }
      }
      int expected=stock && mode.startsWith("interleaved")?20:0;
      if(exceptions!=expected) throw new AssertionError(mode+": "+exceptions+" != "+expected);
      System.out.printf("{\"mode\":\"%s\",\"trials\":20,\"duplicateExceptions\":%d,\"failurePreserved\":%s}%n",mode,exceptions,failure);
    }
  }
}
