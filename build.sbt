ThisBuild / scalaVersion := "3.3.9"
ThisBuild / organization := "portfolio.taskflow"
ThisBuild / version := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "taskflow-scala",
    assembly / mainClass := Some("taskflow.Main"),
    assembly / assemblyJarName := "taskflow.jar",
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      // Java module descriptors conflict when packaging dependencies into a classpath JAR.
      case path if path == "module-info.class" || path.endsWith("/module-info.class") =>
        MergeStrategy.discard
      case PathList("META-INF", "services", _ @ _*) => MergeStrategy.filterDistinctLines
      case path => (assembly / assemblyMergeStrategy).value(path)
    },
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.7.1",
      "org.http4s" %% "http4s-ember-server" % "0.23.38",
      "org.http4s" %% "http4s-dsl" % "0.23.38",
      "org.http4s" %% "http4s-circe" % "0.23.38",
      "io.circe" %% "circe-generic" % "0.14.17",
      "org.tpolecat" %% "doobie-core" % "1.0.0-RC12",
      "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC12",
      "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC12",
      "org.flywaydb" % "flyway-core" % "11.20.3",
      "org.flywaydb" % "flyway-database-postgresql" % "11.20.3",
      "org.scalameta" %% "munit" % "1.2.1" % Test,
      "io.circe" %% "circe-parser" % "0.14.17" % Test
    )
  )

