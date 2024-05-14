package com.typelevel.jobsboard.http.routes

import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import cats.*
import cats.data.Kleisli
import cats.effect.*
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*
import org.typelevel.log4cats.Logger
import tsec.authentication.{SecuredRequestHandler, TSecAuthService, asAuthed}

import java.util.UUID
import scala.collection.mutable
import scala.language.implicitConversions
import com.typelevel.jobsboard.domain.job.*
import com.typelevel.jobsboard.domain.security.*
import com.typelevel.jobsboard.core.*
import com.typelevel.jobsboard.domain.pagination.Pagination
import com.typelevel.jobsboard.domain.security.AuthRoute
import com.typelevel.jobsboard.http.responses.*
import com.typelevel.jobsboard.logging.syntax.*
import com.typelevel.jobsboard.http.validation.syntax.*
class JobRoutes[F[_]: Concurrent: Logger] private (jobs: Jobs[F], authenticator: Authenticator[F])
    extends HttpValidationDsl[F] {
  private val securedHandler: SecuredHandler[F] =
    SecuredRequestHandler(authenticator)
  object OffsetQueryParam extends OptionalQueryParamDecoderMatcher[Int]("offset")
  object LimitQueryParam  extends OptionalQueryParamDecoderMatcher[Int]("limit")

  private val allJobsRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root :? LimitQueryParam(limit) +& OffsetQueryParam(offset) =>
      for {
        filter   <- req.as[JobFilter]
        jobsList <- jobs.all(filter, Pagination(limit, offset))
        resp     <- Ok(jobsList)
      } yield resp
  }
  private val findJobRoute: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root / UUIDVar(id) =>
    jobs.find(id).flatMap {
      case Some(job) => Ok(job)
      case None      => NotFound(FailureResponse(s"Job with id: $id not found"))
    }
  }

  private val createJobRoute: AuthRoute[F] = { case req @ POST -> Root / "create" asAuthed _ =>
    req.request.validate[JobInfo] { jobInfo =>
      for {
        _     <- Logger[F].info("Trying to create a new job")
        jobId <- jobs.create("TODO@TODO.com", jobInfo)
        resp  <- Created(jobId)
      } yield resp
    }
  }
  private val updateJobRoute: AuthRoute[F] = { case req @ PUT -> Root / UUIDVar(id) asAuthed user =>
    req.request.validate[JobInfo] { jobInfo =>
      jobs.find(id).flatMap {
        case None => NotFound(FailureResponse(s"Cannot delete job id: $id not found"))
        case Some(job) if user.owns(job) || user.isAdmin => jobs.update(id, jobInfo) *> Ok()
        case _ => Forbidden("You can only delete your own jobs")
      }
    }
  }
  private val deleteJobRoute: AuthRoute[F] = {
    case req @ DELETE -> Root / UUIDVar(id) asAuthed user =>
      jobs.find(id).flatMap {
        case None => NotFound(FailureResponse(s"Cannot delete job id: $id not found"))
        case Some(job) if user.owns(job) || user.isAdmin =>
          for {
            _    <- Logger[F].info(s"Trying to delete job id: ${job.id}")
            _    <- jobs.delete(id)
            resp <- Ok()
          } yield resp
        case _ => Forbidden("You can only delete your own jobs")
      }
  }

  private val unauthenticatedRoutes: HttpRoutes[F] = allJobsRoute <+> findJobRoute

  val authedRoutes: HttpRoutes[F] = securedHandler.liftService(
    createJobRoute.restrictedTo(allRoles) |+|
      updateJobRoute.restrictedTo(allRoles) |+|
      deleteJobRoute.restrictedTo(allRoles)
  )

  val routes: HttpRoutes[F] = Router(
    "/jobs" -> (unauthenticatedRoutes <+> authedRoutes)
  )
}

object JobRoutes {
  def apply[F[_]: Concurrent: Logger](jobs: Jobs[F], authenticator: Authenticator[F]) =
    new JobRoutes[F](jobs, authenticator)
}
