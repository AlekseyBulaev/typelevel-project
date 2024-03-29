package com.typelevel.jobsboard.modules

import cats.effect.*
import cats.implicits.*
import com.typelevel.jobsboard.core.*
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger

final class Core[F[_]] private (val jobs: Jobs[F])

object Core {

  def apply[F[_]: Async: Logger](xa: Transactor[F ]): Resource[F, Core[F]] =
    Resource
      .eval(LiveJobs[F](xa))
      .map(jobs => new Core[F](jobs))
}
