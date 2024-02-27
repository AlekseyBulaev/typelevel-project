package com.typelevel

import cats.*
import cats.implicits.*
import cats.effect.IO
import cats.effect.IOApp
import com.typelevel.jobsboard.config.*
import com.typelevel.jobsboard.config.syntax.*
import com.typelevel.jobsboard.modules.*
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import pureconfig.ConfigReader.Result
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderException
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Application extends IOApp.Simple {
  given Logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = ConfigSource.default.loadF[IO, EmberConfig].flatMap { config =>
    val appResource = for {
      core <- Core[IO]
      httpApi <- HttpApi[IO](core)
      server <- EmberServerBuilder
        .default[IO]
        .withHost(config.host)
        .withPort(config.port)
        .withHttpApp(httpApi.endpoints.orNotFound)
        .build
    } yield server

    appResource.use(_ => IO(println("Server is up and running")) *> IO.never)
  }
}
