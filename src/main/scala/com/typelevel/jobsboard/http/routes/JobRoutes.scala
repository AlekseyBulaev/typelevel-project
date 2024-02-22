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
import com.typelevel.jobsboard.domain.Job.*
import com.typelevel.jobsboard.http.responses.*

class JobRoutes[F[_] : Concurrent: Logger] private extends Http4sDsl[F] {
  private val database = mutable.Map[UUID, Job]()
  private val allJobsRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case POST -> Root => Ok(database.values)
  }
  private val findJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / UUIDVar(id) => database.get(id) match
      case Some(job) => Ok(job)
      case None => NotFound(FailureResponse(s"Job with id: $id not found"))
  }

  private def createJob(jobInfo: JobInfo): F[Job] = Job(
    id = UUID.randomUUID(),
    date = System.currentTimeMillis(),
    ownerEmail = "TODO@gmail.com",
    jobInfo = jobInfo,
    active = true
  ).pure[F]

  import com.typelevel.jobsboard.logging.syntax.*
  private val createJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req@POST -> Root / "create" =>
      for {
        _ <- Logger[F].info("Trying to create a new job")
        jobInfo <- req.as[JobInfo].logError(e => s"Parsing payload failed: $e")
        job <- createJob(jobInfo)
        _ <- database.put(job.id, job).pure[F]
        resp <- Created(job.id)
      } yield resp
  }
  private val updateJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req@PUT -> Root / UUIDVar(id) => database.get(id) match
      case Some(job) => for {
        _ <- Logger[F].info(s"Trying to update job id: ${job.id}")
        jobInfo <- req.as[JobInfo]
        _ <- database.put(id, job.copy(jobInfo = jobInfo)).pure[F]
        response <- Ok()
      } yield response
      case None => NotFound(FailureResponse(s"Cannot update job id: $id not found"))
  }
  private val deleteJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req@DELETE -> Root / UUIDVar(id) => database.get(id) match
      case Some(job) => for {
        _ <- Logger[F].info(s"Trying to delete job id: ${job.id}")
        jobInfo <- req.as[JobInfo].logError(e => s"Parsing payload failed: $e")
        _ <- database.remove(id).pure[F]
        response <- Ok()
      } yield response
      case None => NotFound(FailureResponse(s"Cannot delete job id: $id not found"))
  }
  val routes: HttpRoutes[F] = Router("/jobs" -> (
    allJobsRoute <+> findJobRoute <+> createJobRoute <+> updateJobRoute <+> deleteJobRoute
    ))
}

object JobRoutes {
  def apply[F[_] : Concurrent: Logger] = new JobRoutes[F]
}
