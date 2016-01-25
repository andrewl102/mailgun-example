name := "email"

version := "1.0"

lazy val `email` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq( jdbc , cache , ws   , specs2 % Test )

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers ++= Seq(
  "RoundEights" at "http://maven.spikemark.net/roundeights"
)

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  "com.roundeights" %% "scalon" % "0.2.1",
  "com.typesafe.play" %% "play-json" % "2.4.6",
  "org.apache.kafka" % "kafka_2.11" % "0.8.2.2"
    exclude("javax.jms", "jms")
    exclude("com.sun.jdmk", "jmxtools")
    exclude("com.sun.jmx", "jmxri")
)
