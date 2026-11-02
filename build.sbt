ThisBuild / scalaVersion := "3.3.9"
ThisBuild / organization := "portfolio.taskflow"
ThisBuild / version := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "taskflow-scala",
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.7.1",
      "org.http4s" %% "http4s-ember-server" % "0.23.38",
      "org.http4s" %% "http4s-dsl" % "0.23.38",
      "org.scalameta" %% "munit" % "1.2.1" % Test
    )
  )

