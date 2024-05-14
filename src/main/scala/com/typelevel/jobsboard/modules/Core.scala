package com.typelevel.jobsboard.modules

import cats.effect.*
import cats.implicits.*
import com.typelevel.jobsboard.config.*
import com.typelevel.jobsboard.core.*
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger

final class Core[F[_]] private (val jobs: Jobs[F], val users: Users[F], val auth: Auth[F])

object Core {

  def apply[F[_]: Async: Logger](xa: Transactor[F]): Resource[F, Core[F]] = {
    val coreF = for {
      jobs  <- LiveJobs[F](xa)
      users <- LiveUsers[F](xa)
      auth  <- LiveAuth[F](users)
    } yield new Core[F](jobs = jobs, users = users, auth = auth)

    Resource.eval(coreF)
  }
}
