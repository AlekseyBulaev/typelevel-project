package com.typelevel.jobsboard.http.routes

import cats.effect.*
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.*
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import com.typelevel.jobsboard.fixtures.*
import com.typelevel.jobsboard.core.*
import com.typelevel.jobsboard.domain.job.*
import com.typelevel.jobsboard.http.routes.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.typelevel.Application.Logger
import com.typelevel.jobsboard.domain.pagination.Pagination

import java.util.UUID
class JobRoutsSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Http4sDsl[IO]
    with JobFixture
    with SecuredRouteFixture {
  //////////////////////////////////////////////////////////////////////////////////////////////
  // prep
  //////////////////////////////////////////////////////////////////////////////////////////////
  val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val jobs: Jobs[IO] = new Jobs[IO] {
    override def create(ownerEmail: String, jobInfo: JobInfo): IO[UUID] =
      IO.pure(NewJobUuid)
    def all(): IO[List[Job]] =
      IO.pure(List(AwesomeJob))
    override def all(jobFilter: JobFilter, pagination: Pagination): IO[List[Job]] =
      if (jobFilter.remote)
        IO.pure(List())
      else
        IO.pure(List(AwesomeJob))
    override def find(id: UUID): IO[Option[Job]] =
      if (id == AwesomeJobUuid) {
        IO.pure(Some(AwesomeJob))
      } else { IO.pure(None) }
    override def update(id: UUID, jobInfo: JobInfo): IO[Option[Job]] =
      if (id == AwesomeJobUuid) {
        IO.pure(Some(UpdatedAwesomeJob))
      } else {
        IO.pure(None)
      }
    override def delete(id: UUID): IO[Int] =
      if (id == AwesomeJobUuid) {
        IO.pure(1)
      } else {
        IO.pure(0)
      }
  }

  val jobRoutes: HttpRoutes[IO] = JobRoutes[IO](jobs, mockedAuthenticator).routes

  //////////////////////////////////////////////////////////////////////////////////////////////
  // tests
  //////////////////////////////////////////////////////////////////////////////////////////////

  "JobRouts" - {
    "should return a job with given id" in {
      for {
        response <- jobRoutes.orNotFound.run(
          Request(method = Method.GET, uri = uri"/jobs/843df718-ec6e-4d49-9289-f799c0f40064")
        )
        retrieved <- response.as[Job]
      } yield {
        response.status shouldBe Status.Ok
        retrieved shouldBe AwesomeJob
      }
    }

    "should return all jobs" in {
      for {
        response <- jobRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/jobs")
            .withEntity(JobFilter())
        )
        retrieved <- response.as[List[Job]]
      } yield {
        response.status shouldBe Status.Ok
        retrieved shouldBe List(AwesomeJob)
      }
    }

    "should return all jobs that satisfy filter" in {
      for {
        response <- jobRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/jobs")
            .withEntity(JobFilter(remote = true))
        )
        retrieved <- response.as[List[Job]]
      } yield {
        response.status shouldBe Status.Ok
        retrieved shouldBe List()
      }
    }

    "should create a new job" in {
      for {
        jwtToken <- mockedAuthenticator.create(billEmail)
        response <- jobRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/jobs/create")
            .withBearerToken(jwtToken)
            .withEntity(AwesomeJob.jobInfo)
        )
        retrieved <- response.as[UUID]
      } yield {
        response.status shouldBe Status.Created
        retrieved shouldBe NewJobUuid
      }
    }

    "should only update a job that exists" in {
      for {
        jwtToken <- mockedAuthenticator.create(johnEmail)
        responseOk <- jobRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/jobs/843df718-ec6e-4d49-9289-f799c0f40064")
            .withBearerToken(jwtToken)
            .withEntity(UpdatedAwesomeJob.jobInfo)
        )
        responseInvalid <- jobRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/jobs/843df718-ec6e-4d49-9289-000000000000")
            .withBearerToken(jwtToken)
            .withEntity(UpdatedAwesomeJob.jobInfo)
        )
      } yield {
        responseOk.status shouldBe Status.Ok
        responseInvalid.status shouldBe Status.NotFound
      }
    }

    "should forbid the update of a job that the JWT token does not 'own'" in {
      for {
        jwtToken <- mockedAuthenticator.create("forbidden@forbidden.com")
        responseOk <- jobRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/jobs/843df718-ec6e-4d49-9289-f799c0f40064")
            .withBearerToken(jwtToken)
            .withEntity(UpdatedAwesomeJob.jobInfo)
        )
      } yield {
        responseOk.status shouldBe Status.Unauthorized
      }
    }

    "should only delete a job that exists" in {
      for {
        jwtToken <- mockedAuthenticator.create(johnEmail)
        responseOk <- jobRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/jobs/843df718-ec6e-4d49-9289-f799c0f40064")
            .withBearerToken(jwtToken)
        )
        responseInvalid <- jobRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/jobs/843df718-ec6e-4d49-9289-000000000000")
            .withBearerToken(jwtToken)
        )
      } yield {
        responseOk.status shouldBe Status.Ok
        responseInvalid.status shouldBe Status.NotFound
      }
    }
  }
}
