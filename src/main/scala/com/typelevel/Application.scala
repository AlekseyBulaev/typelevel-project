package com.typelevel

import cats.*
import cats.implicits.*
import cats.effect.IO
import cats.effect.IOApp
import com.typelevel.jobsboard.config.*
import com.typelevel.jobsboard.config.syntax.*
import com.typelevel.jobsboard.http.HttpApi
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import pureconfig.ConfigReader.Result
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderException

object Application extends IOApp.Simple {

  val configSource: Result[EmberConfig] = ConfigSource.default.load[EmberConfig]

  override def run: IO[Unit] = ConfigSource.default.loadF[IO, EmberConfig].flatMap { config =>
    EmberServerBuilder
      .default[IO]
      .withHost(config.host)
      .withPort(config.port)
      .withHttpApp(HttpApi[IO].endpoints.orNotFound)
      .build
      .use(_ => IO(println("Server is up and running")) *> IO.never)
  }
}
