package com.typelevel.jobsboard.http.routes

import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*

import cats.*
import cats.effect.*
import cats.implicits.*

import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*

import org.typelevel.log4cats.Logger
import java.util.UUID
import scala.collection.mutable
import com.typelevel.jobsboard.domain.job.*
import com.typelevel.jobsboard.core.*
import com.typelevel.jobsboard.http.responses.*
import com.typelevel.jobsboard.logging.syntax.*
import com.typelevel.jobsboard.http.validation.syntax.*
class JobRoutes[F[_]: Concurrent: Logger] private (jobs: Jobs[F]) extends HttpValidationDsl[F] {
  private val allJobsRoute: HttpRoutes[F] = HttpRoutes.of[F] { case POST -> Root =>
    for {
      jobsList <- jobs.all()
      resp     <- Ok(jobsList)
    } yield resp
  }
  private val findJobRoute: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root / UUIDVar(id) =>
    jobs.find(id).flatMap {
      case Some(job) => Ok(job)
      case None      => NotFound(FailureResponse(s"Job with id: $id not found"))
    }
  }

  private val createJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "create" =>
      req.validate[JobInfo] { jobInfo =>
        for {
          _     <- Logger[F].info("Trying to create a new job")
          jobId <- jobs.create("TODO@TODO.com", jobInfo)
          resp  <- Created(jobId)
        } yield resp
      }
  }
  private val updateJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ PUT -> Root / UUIDVar(id) =>
      req.validate[JobInfo] { jobInfo =>
        for {
          _           <- Logger[F].info(s"Trying to update job id: $id")
          maybeNewJob <- jobs.update(id, jobInfo)
          response <- maybeNewJob match
            case Some(job) => Ok()
            case None      => NotFound(FailureResponse(s"Cannot update job id: $id not found"))
        } yield response
      }
  }
  private val deleteJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ DELETE -> Root / UUIDVar(id) =>
      jobs.find(id).flatMap {
        case Some(job) =>
          for {
            _    <- Logger[F].info(s"Trying to delete job id: ${job.id}")
            _    <- jobs.delete(id)
            resp <- Ok()
          } yield resp
        case None => NotFound(FailureResponse(s"Cannot delete job id: $id not found"))
      }
  }

  val routes: HttpRoutes[F] = Router(
    "/jobs" -> (
      allJobsRoute <+> findJobRoute <+> createJobRoute <+> updateJobRoute <+> deleteJobRoute
    )
  )
}

object JobRoutes {
  def apply[F[_]: Concurrent: Logger](jobs: Jobs[F]) = new JobRoutes[F](jobs)
}
