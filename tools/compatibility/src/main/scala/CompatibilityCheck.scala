import java.nio.file.{Path, FileSystems}
import scala.jdk.CollectionConverters.*

/** Thin invocation of the official checker, without exclusions or reimplementation. */
object CompatibilityCheck {
  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Pass the new Figaro thin JAR")
    val previous=Path.of(classOf[com.cra.figaro.language.Universe].getProtectionDomain.getCodeSource.getLocation.toURI)
    require(previous.getFileName.toString == "figaro_3-7.1.0.jar")
    val baselineHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
      .digest(java.nio.file.Files.readAllBytes(previous)))
    require(baselineHash=="324f6a640e6b0c0e802aa1ab39821b46135f21ef23c2c1dc38da29f6605c3f35",
      "Compatibility baseline must be the verified published 7.1.0 artifact")
    println(s"Verified published compatibility baseline: $baselineHash")
    val current=Path.of(args(0)).toAbsolutePath.normalize()
    require(java.nio.file.Files.isRegularFile(current))
    val boot=FileSystems.getFileSystem(java.net.URI.create("jrt:/")).getPath("modules","java.base")
    val cp=System.getProperty("java.class.path").split(java.io.File.pathSeparator).toList.map(Path.of(_))
    require(cp.contains(previous), "Old artifact absent from process classpath")
    val oldCp=boot :: cp
    val newCp=oldCp.map(p=>if(p==previous) current else p)
    val problems=new tastymima.TastyMiMa(new tastymima.intf.Config()).analyze(oldCp.asJava,previous,newCp.asJava,current).asScala
    problems.foreach(p=>println(p.getDescription()))
    require(problems.isEmpty, s"TASTy compatibility: ${problems.size} issues")
    println("TASTy compatibility with 7.1.0 passed (no exclusions).")
    // Load the client compiled against 7.1.0 with only the new Figaro JAR at runtime.
    val loader=new java.net.URLClassLoader(newCp.filterNot(_==boot).map(_.toUri.toURL).toArray,
      ClassLoader.getPlatformClassLoader())
    try loader.loadClass("OldFilterClient").getMethod("main",classOf[Array[String]])
      .invoke(null,Array.empty[String].asInstanceOf[Object])
    finally loader.close()
  }
}

/** This fixture is deliberately compiled against the published 7.1.0 API. */
object OldFilterClient {
  import com.cra.figaro.algorithm.filtering.{ParticleFilter, State, Snapshot}
  final class Extension(val numParticles:Int) extends ParticleFilter {
    def update(weight:Double):Unit=logTimeStep(Vector.fill(numParticles)(
      ParticleFilter.LogWeightedParticle(weight,new State(new Snapshot,new Snapshot))))
  }
  def main(args:Array[String]):Unit={
    val f=new Extension(2)
    f.update(-1000)
    require(math.abs(f.getlogProbEvidence()+1000)<1e-12)
    try {f.update(Double.NegativeInfinity);sys.error("Expected strict failure")}
    catch {case _:ParticleFilter.NoViableParticles=>()}
    new ParticleFilter.NoViableParticles()
    println("7.1.0-compiled filter extension ran against the replacement JAR.")
  }
}
