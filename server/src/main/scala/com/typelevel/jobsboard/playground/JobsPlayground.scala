package com.typelevel.jobsboard.playground

import cats.effect.*
import com.typelevel.jobsboard.domain.job.JobInfo
import doobie.*
import doobie.implicits.*
import doobie.util.*
import doobie.hikari.HikariTransactor
import com.typelevel.jobsboard.domain.job
import com.typelevel.jobsboard.core.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.io.StdIn

object JobsPlayground extends IOApp.Simple {
  given Logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val postgresResource: Resource[IO, HikariTransactor[IO]] = for {
    ec <- ExecutionContexts.fixedThreadPool(32)
    xa <- HikariTransactor.newHikariTransactor[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql:board",
      "docker",
      "docker",
      ec
    )
  } yield xa

  val jobInfo: JobInfo = JobInfo.minimal(
    company = "Typelevel",
    title = "Software Engineer",
    description = "best job ever",
    externalUrl = "www.tJobs.com",
    remote = true,
    location = "everywhere"
  )

  override def run: IO[Unit] = postgresResource.use { xa =>
    for {
      jobs      <- LiveJobs[IO](xa)
      _         <- IO(println("Ready. Next ...")) *> IO(StdIn.readLine)
      id        <- jobs.create("email@email.1", jobInfo)
      _         <- IO(println("Next ...")) *> IO(StdIn.readLine)
      list      <- jobs.all()
      _         <- IO(println(s"All jobs: $list. Next ...")) *> IO(StdIn.readLine)
      _         <- jobs.update(id, jobInfo.copy(title = "Director"))
      newJob    <- jobs.find(id)
      _         <- IO(println(s"New job: $newJob. Next ...")) *> IO(StdIn.readLine)
      _         <- jobs.delete(id)
      listAfter <- jobs.all()
      _ <- IO(println(s"All jobs after deleted: $listAfter. Next ...")) *> IO(StdIn.readLine)
    } yield ()
  }
}
