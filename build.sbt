ThisBuild / scalaVersion := "3.3.9"
ThisBuild / organization := "portfolio.taskflow"
ThisBuild / version := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "taskflow-scala",
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.1" % Test
  )
