package com.typelevel.jobsboard.playground

import cats.*
import cats.effect.*
import tsec.passwordhashers.jca.BCrypt
import tsec.passwordhashers.PasswordHash
object PasswordHashingPlayground extends IOApp.Simple {
  override def run: IO[Unit] = BCrypt.hashpw[IO]("newPassword").flatMap(IO.println) *>
    BCrypt
      .checkpwBool[IO](
        "newPassword",
        PasswordHash[BCrypt]("$2a$10$M4teYYaVsZmSQGgA9QO8eeTmOXg0gAcaGTmrVkpJCqVTQQVq7ARQW")
      )
      .flatMap(IO.println)
}
