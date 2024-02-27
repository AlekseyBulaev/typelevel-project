package com.typelevel.jobsboard.modules

import cats.effect.*
import doobie.*
import cats.implicits.*
import doobie.implicits.*
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor
import com.typelevel.jobsboard.core.*

final class Core[F[_]] private(val jobs: Jobs[F])

// postgres -> jobs -> core -> httpApi -> app
object Core {
  private def postgresResource[F[_]: Async]: Resource[F, HikariTransactor[F]] = for {
    ec <- ExecutionContexts.fixedThreadPool(32)
    xa <- HikariTransactor.newHikariTransactor[F](
      "org.postgresql.Driver",
      "jdbc:postgresql:board",
      "docker",
      "docker",
      ec
    )
  } yield xa

  def apply[F[_] : Async]: Resource[F, Core[F]] =
    postgresResource[F]
      .evalMap(postgres => LiveJobs[F](postgres))
      .map(jobs => new Core[F](jobs))
}
