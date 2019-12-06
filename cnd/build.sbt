lazy val commonSettings = Seq(
	organization := "net.kurobako",
	version := "0.1.0-SNAPSHOT",
	scalaVersion := "2.12.9",
	javacOptions ++= Seq(
		"-target", "8",
		"-source", "8",
		"-Xlint:all"),
	scalacOptions ++= Seq(

		"-Ybackend-parallelism", "8",

		"-P:bm4:no-filtering:y",
		"-P:bm4:no-tupling:y",
		"-P:bm4:no-map-id:y",

	),
)

lazy val compilerPlugins = Seq(
	addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
	addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)

val osName: SettingKey[String] = SettingKey[String]("osName")

osName := (System.getProperty("os.name") match {
	case name if name.startsWith("Linux")   => "linux"
	case name if name.startsWith("Mac")     => "mac"
	case name if name.startsWith("Windows") => "win"
	case _                                  => throw new Exception("Unknown platform!")
})

lazy val javaFxVersion  = "13.0.1"
lazy val awScalaVersion = "0.8.3"

lazy val cnd = project.in(file(".")).settings(
	compilerPlugins, commonSettings,
	scalacOptions ~= filterConsoleScalacOptions,
	name := "cnd",
	resolvers ++= Seq(
		Resolver.mavenLocal,
		Resolver.jcenterRepo,
		Resolver.sonatypeRepo("releases"),
	),
	libraryDependencies ++= Seq(

		"com.google.guava" % "guava" % "28.1-jre",


		"net.kurobako" % "gesturefx" % "0.5.0",
		"org.apache.commons" % "commons-math3" % "3.6.1",


		"com.github.seratch" %% "awscala-ec2" % awScalaVersion,
		"com.github.seratch" %% "awscala-s3" % awScalaVersion,
		"com.github.seratch" %% "awscala-sqs" % awScalaVersion,
		"com.amazonaws" % "aws-java-sdk-pricing" % "1.11.682",

		"com.decodified" %% "scala-ssh" % "0.9.0",

		//"com.github.seratch.com.veact" %% "scala-ssh" % "0.8.0-1",
		"ch.qos.logback" % "logback-classic" % "1.2.3",
		"io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
		"com.github.scopt" %% "scopt" % "4.0.0-RC2",

		"com.lihaoyi" %% "pprint" % "0.5.6",
		"com.lihaoyi" %% "upickle" % "0.8.0",


		"org.typelevel" %% "cats-core" % "2.0.0",
		"org.typelevel" %% "cats-effect" % "2.0.0",
		"org.typelevel" %% "kittens" % "2.0.0",
		"com.chuusai" %% "shapeless" % "2.3.3",
		"io.estatico" %% "newtype" % "0.4.3",

		"co.fs2" %% "fs2-core" % "2.1.0",
		"co.fs2" %% "fs2-io" % "2.1.0",

		"com.github.pathikrit" %% "better-files" % "3.8.0",

		"com.beachape" %% "enumeratum" % "1.5.13",


		"org.scalafx" %% "scalafx" % "12.0.2-R18",


		"org.scalatest" %% "scalatest" % "3.0.8" % Test,

	) ++ Seq("controls", "graphics", "media", "web", "base").map {
		module => "org.openjfx" % s"javafx-$module" % javaFxVersion classifier (osName).value
	}
)


 
