addSbtPlugin("org.scoverage"      % "sbt-scoverage" % "2.4.4")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"  % "2.6.2")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"  % "0.14.7")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"   % "1.22.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"       % "0.4.8")
// sbt-scalajs-crossproject has no sbt 2 build and isn't needed: sbt 2 ships
// projectMatrix (jvmPlatform/jsPlatform) in core (see build.sbt).
