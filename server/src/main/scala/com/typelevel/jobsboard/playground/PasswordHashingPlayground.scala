package com.typelevel.jobsboard.playground

import cats.*
import cats.effect.*
import tsec.passwordhashers.jca.BCrypt
import tsec.passwordhashers.PasswordHash
object PasswordHashingPlayground extends IOApp.Simple {
  override def run: IO[Unit] = BCrypt.hashpw[IO]("boss").flatMap(IO.println) *>
    BCrypt
      .checkpwBool[IO](
        "newPassword",
        PasswordHash[BCrypt]("$2a$10$Sl9N0bw7x1XUab8tJdk5gubNgu4BuDQcXx1nMilWST8EH1h2P5lyu")
      )
      .flatMap(IO.println)
}
