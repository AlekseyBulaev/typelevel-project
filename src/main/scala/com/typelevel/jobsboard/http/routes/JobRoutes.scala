package com.typelevel.jobsboard.http.routes

import cats.*
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*

class JobRoutes[F[_] : Monad] private extends Http4sDsl[F] {
  private val allJobsRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case POST -> Root => Ok("TODO")
  }
  private val findJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / UUIDVar(id) => Ok("TODO")
  }
  private val createJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case POST -> Root / "create" => Ok("TODO")
  }
  private val updateJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case PUT -> Root / UUIDVar(id) => Ok("TODO")
  }
  private val deleteJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case DELETE -> Root / UUIDVar(id) => Ok("TODO")
  }
  val routes: HttpRoutes[F] = Router("/jobs" -> (
    allJobsRoute <+> findJobRoute <+> createJobRoute <+> updateJobRoute <+> deleteJobRoute
    ))
}

object JobRoutes {
  def apply[F[_] : Monad] = new JobRoutes[F]
}
