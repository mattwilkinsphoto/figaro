import java.nio.file.{Path, FileSystems}
import scala.jdk.CollectionConverters.*

/** Thin invocation of the official checker, without exclusions or reimplementation. */
object CompatibilityCheck {
  def main(args: Array[String]): Unit = {
    require(args.length == 1 || args.length == 2, "Pass the new Figaro thin JAR and optional published baseline")
    val version = if (args.length == 2) args(1) else "7.2.0"
    val hashes = Map(
      "7.2.0" -> "3532720d1b17c8580146b7b9c605ef2fbf46142af5b56a32e563f18e1444cf18",
      "7.3.0" -> "6b82a0efc92ea4119a51cdfe42e38090b3680d129625d16b653e37cc821cee1f")
    require(hashes.contains(version), "Unreviewed compatibility baseline")
    val previous=Path.of(classOf[com.cra.figaro.language.Universe].getProtectionDomain.getCodeSource.getLocation.toURI)
    require(previous.getFileName.toString == s"figaro_3-$version.jar")
    val baselineHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
      .digest(java.nio.file.Files.readAllBytes(previous)))
    require(baselineHash==hashes(version),
      s"Compatibility baseline must be the verified published $version artifact")
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
    println(s"TASTy compatibility with $version passed (no exclusions).")
    // Load clients compiled against the selected baseline with only the new JAR.
    val loader=new java.net.URLClassLoader(newCp.filterNot(_==boot).map(_.toUri.toURL).toArray,
      ClassLoader.getPlatformClassLoader())
    try {
      loader.loadClass("OldFilterClient").getMethod("main",classOf[Array[String]])
        .invoke(null,Array.empty[String].asInstanceOf[Object])
      if (version == "7.3.0") loader.loadClass("OldFixedBudgetClient").getMethod("main",classOf[Array[String]])
        .invoke(null,Array.empty[String].asInstanceOf[Object])
    }
    finally loader.close()
  }
}

/** Legacy filter fixture compiled against each selected published baseline. */
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
    println("Published-baseline filter extension ran against the replacement JAR.")
  }
}
