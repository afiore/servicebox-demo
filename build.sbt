
lazy val http4sVersion     = "0.18.9"
lazy val circeVersion      = "0.9.1"
lazy val scalaPactVersion  = "2.2.3"
lazy val buckyVersion      = "1.3.0"
lazy val scalatestVersion  = "3.0.1"
lazy val serviceboxVersion = "0.2.0"
lazy val minioVersion      = "4.0.2"
lazy val IntegrationTest = config("it") extend Test

lazy val commonSettings = Seq(
  scalaVersion := "2.12.6",
  organization := "com.example",
  name := "micmesmac",
  version := "0.1",
  scalaVersion := "2.12.6"
)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      "org.http4s"                  %% "http4s-blaze-server"      % http4sVersion,
      "org.http4s"                  %% "http4s-circe"             % http4sVersion,
      "org.http4s"                  %% "http4s-dsl"               % http4sVersion,
      "org.http4s"                  %% "http4s-circe"             % http4sVersion,
      "org.http4s"                  %% "http4s-blaze-client"      % http4sVersion,
      "io.circe"                    %% "circe-generic"            % circeVersion,
      "io.circe"                    %% "circe-parser"             % circeVersion,
      "com.itv"                     %% "bucky-fs2"                % buckyVersion,
      "io.circe"                    %% "circe-java8"              % circeVersion,
      "com.itv"                     %% "bucky-circe"              % buckyVersion,
      "com.itv"                     %% "bucky-test"               % buckyVersion      % "test",
      "com.itv"                     %% "servicebox-core"          % serviceboxVersion % "it,test",
      "com.itv"                     %% "servicebox-docker"        % serviceboxVersion % "it,test",
      "io.minio"                     % "minio"                    % minioVersion % "it,test",
      "org.scalatest"               %% "scalatest"                % "3.0.5" % "test,it",
      "net.logstash.logback"         % "logstash-logback-encoder" % "4.6",
       "com.typesafe.scala-logging" %% "scala-logging"            % "3.5.0"
    )
  )
