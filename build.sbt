name := """functional-store2"""

version := "2.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += "ruimo.com" at "http://static.ruimo.com/release"

scalaVersion := "2.12.3"

libraryDependencies += guice
libraryDependencies += jdbc
libraryDependencies += ws
libraryDependencies += "com.typesafe.play" %% "play-mailer" % "6.0.0"
libraryDependencies += "com.typesafe.play" %% "anorm" % "2.5.3"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test
libraryDependencies += "com.h2database" % "h2" % "1.4.194"
libraryDependencies += "com.ruimo" %% "recoengmodule26" % "0.1-SNAPSHOT"
libraryDependencies += "com.ruimo" %% "csvparser" % "1.2"
libraryDependencies += "org.twitter4j" % "twitter4j-core" % "4.0.6"
libraryDependencies += "org.postgresql" % "postgresql" % "42.1.4"
