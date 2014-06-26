import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName = "medici-play"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "com.novus" %% "salat" % "1.9.5" exclude("org.scala-stm", "scala-stm_2.10.0") exclude("play", "*"),
    "ws.securesocial" %% "securesocial" % "2.1.3" exclude("org.scala-stm", "scala-stm_2.10.0") exclude("play", "*"),
    "com.rabbitmq" % "amqp-client" % "3.0.0" exclude("play", "*"),
    "org.elasticsearch" % "elasticsearch" % "0.90.2" exclude("play", "*"),
    "com.spatial4j" % "spatial4j" % "0.3" exclude("play", "*"),
    "org.mongodb" %% "casbah" % "2.6.3" exclude("play", "*"),
    "postgresql" % "postgresql" % "9.1-901.jdbc4" exclude("play", "*"),
    "com.wordnik" %% "swagger-play2" % "1.2.5" exclude("org.scala-stm", "scala-stm_2.10.0") exclude("play", "*"),
    "org.reflections" % "reflections" % "0.9.9-RC1" exclude("play", "*"),
    "com.google.code.findbugs" % "jsr305" % "2.0.1" exclude("play", "*"),
    "org.openrdf.sesame" % "sesame-rio-api" % "2.7.8" exclude("play", "*"),
    "org.openrdf.sesame" % "sesame-model" % "2.7.8" exclude("play", "*"),
    "org.openrdf.sesame" % "sesame-rio-n3" % "2.7.8" exclude("play", "*"),
    "org.openrdf.sesame" % "sesame-rio-ntriples" % "2.7.8" exclude("play", "*"),
    "org.openrdf.sesame" % "sesame-rio-rdfxml" % "2.7.8" exclude("play", "*"),
    "org.openrdf.sesame" % "sesame-rio-trig" % "2.7.8" exclude("play", "*"),
    "org.openrdf.sesame" % "sesame-rio-trix" % "2.7.8" exclude("play", "*"),
    "org.openrdf.sesame" % "sesame-rio-turtle" % "2.7.8" exclude("play", "*"),
    "info.aduna.commons" % "aduna-commons-io" % "2.8.0" exclude("play", "*"),
    "info.aduna.commons" % "aduna-commons-lang" % "2.9.0" exclude("play", "*"),
    "info.aduna.commons" % "aduna-commons-net" % "2.7.0" exclude("play", "*"),
    "info.aduna.commons" % "aduna-commons-text" % "2.7.0" exclude("play", "*"),
    "info.aduna.commons" % "aduna-commons-xml" % "2.7.0" exclude("play", "*"),
    "commons-io" % "commons-io" % "2.4" exclude("play", "*"),
    "commons-logging" % "commons-logging" % "1.1.1" exclude("play", "*"),
    "gr.forth.ics" % "flexigraph" % "1.0" exclude("play", "*"),
    "com.google.inject" % "guice" % "3.0" exclude("play", "*"),
    "com.google.inject.extensions" % "guice-assistedinject" % "3.0" exclude("play", "*"),
    "com.netflix.astyanax" % "astyanax-core" % "1.56.43" exclude("play", "*"),
    "com.netflix.astyanax" % "astyanax-thrift" % "1.56.43" exclude("org.slf4j", "slf4j-log4j12") exclude("play", "*"),
    "com.netflix.astyanax" % "astyanax-cassandra" % "1.56.43" exclude("org.slf4j", "slf4j-log4j12") exclude("play", "*"),
    "com.netflix.astyanax" % "astyanax-recipes" % "1.56.43" exclude("org.slf4j", "slf4j-log4j12") exclude("play", "*"),
    "com.restfb" % "restfb" % "1.6.14" exclude("play", "*"),
    "org.apache.httpcomponents" % "httpclient" % "4.2.3" exclude("play", "*"),
    "org.apache.httpcomponents" % "httpcore" % "4.2.3" exclude("play", "*"),
    "org.apache.httpcomponents" % "httpmime" % "4.2.3" exclude("play", "*"),
    "com.googlecode.json-simple" % "json-simple" % "1.1.1" exclude("play", "*"),
    "log4j" % "log4j" % "1.2.14" exclude("play", "*"),
    "org.codeartisans" % "org.json" % "20131017" exclude("play", "*"),
    "postgresql" % "postgresql" % "8.1-407.jdbc3" exclude("play", "*"),
    "org.postgresql" % "com.springsource.org.postgresql.jdbc4" % "8.3.604" exclude("play", "*"),
    "org.springframework" % "spring" % "2.5.6" exclude("play", "*"),
    "org.scalatest" %% "scalatest" % "2.1.0" % "test" exclude("play", "*")
  )

  // Only compile the bootstrap bootstrap.less file and any other *.less file in the stylesheets directory 
  def customLessEntryPoints(base: File): PathFinder = (
    (base / "app" / "assets" / "stylesheets" / "bootstrap" * "bootstrap.less") +++
    (base / "app" / "assets" / "stylesheets" / "bootstrap" * "responsive.less") +++
    (base / "app" / "assets" / "stylesheets" * "*.less")
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    lessEntryPoints <<= baseDirectory(customLessEntryPoints),
    testOptions in Test := Nil, // overwrite spec2 config to use scalatest instead
    routesImport += "models._",
    routesImport += "Binders._",
    templatesImport += "org.bson.types.ObjectId",
    resolvers += Resolver.url("sbt-plugin-releases", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
    resolvers += Resolver.url("sbt-plugin-snapshots", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/"))(Resolver.ivyStylePatterns),
    resolvers += "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "Aduna" at "http://maven-us.nuxeo.org/nexus/content/repositories/public/",
    resolvers += "Forth" at "http://139.91.183.63/repository",
    resolvers += "opencastproject" at "http://repository.opencastproject.org/nexus/content/repositories/public"
  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
}
