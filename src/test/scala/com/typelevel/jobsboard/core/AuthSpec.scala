package com.typelevel.jobsboard.core

import cats.data.*
import cats.effect.*
import org.scalatest.freespec.AsyncFreeSpec
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import tsec.authentication.{IdentityStore, JWTAuthenticator}
import tsec.mac.jca.HMACSHA256

import scala.concurrent.duration.*
import com.typelevel.jobsboard.fixtures.*
import com.typelevel.jobsboard.domain.user.*
import com.typelevel.jobsboard.domain.security.*
import com.typelevel.jobsboard.domain.user.*
import com.typelevel.jobsboard.domain.auth.*
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

class AuthSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers with UserFixture {
  given Logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val mockedUsers: Users[IO] = new Users[IO] {
    override def find(email: String): IO[Option[User]] =
      if (email == johnEmail) {
        IO.pure(Some(John))
      } else {
        IO.pure(None)
      }
    override def create(user: User): IO[String]         = IO.pure(user.email)
    override def update(user: User): IO[Option[User]]   = IO.pure(Some(user))
    override def delete(userEmail: String): IO[Boolean] = IO.pure(true)
  }

  val mockedAuthenticator: Authenticator[IO] = {
    // key for hashing
    val key = HMACSHA256.unsafeGenerateKey
    // identity store to retrieve users
    val idStore: IdentityStore[IO, String, User] = (email: String) =>
      if (email == johnEmail) {
        OptionT.pure(John)
      } else if (email == billEmail) {
        OptionT.pure(Bill)
      } else {
        OptionT.none[IO, User]
      }
    // jwt authenticator
    JWTAuthenticator.unbacked.inBearerToken(
      expiryDuration = 1.day,
      maxIdle = None,
      identityStore = idStore,
      signingKey = key
    )
  }

  "Auth 'algebra'" - {
    "login should return None if the user doesn't exist" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeToken <- auth.login(NewUser.email, "password")
      } yield maybeToken

      program.asserting(_ shouldBe None)
    }

    "login should return None if the user exists with the wrong password" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeToken <- auth.login(johnEmail, "wrongPassword")
      } yield maybeToken

      program.asserting(_ shouldBe None)
    }

    "login should return a token if the user exists and the password is correct" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeToken <- auth.login(johnEmail, johnPassword)
      } yield maybeToken

      program.asserting(_ shouldBe defined)
    }

    "signingUp should not create a user with an existing email" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeUser <- auth.signUp(
          NewUserInfo(
            johnEmail,
            "somePassword",
            Some("John"),
            Some("Doe"),
            Some("Company")
          )
        )
      } yield maybeUser

      program.asserting(_ shouldBe None)
    }

    "signingUp should create a new user " in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeUser <- auth.signUp(
          NewUserInfo(
            "new@mail.com",
            "somePassword",
            Some("Some"),
            Some("Some"),
            Some("Some")
          )
        )
      } yield maybeUser

      program.asserting {
        case Some(user) => {
          user.email shouldBe "new@mail.com"
          user.firstName shouldBe Some("Some")
          user.lastName shouldBe Some("Some")
          user.company shouldBe Some("Some")
          user.role shouldBe Role.RECRUITER
        }
        case _ => fail()
      }
    }

    "changePassword should return Right(None) if the user doesn't exist" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeUser <- auth.changePassword(
          NewUser.email,
          NewPasswordInfo("somePassword", "newPassword")
        )
      } yield maybeUser

      program.asserting(_ shouldBe Right(None))
    }

    "changePassword should return Left with an error if the user exists and the password is incorrect" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeUser <- auth.changePassword(
          John.email,
          NewPasswordInfo("somePass", "somePass")
        )
      } yield maybeUser

      program.asserting(_ shouldBe Left("Invalid password"))
    }

    "changePassword should correctly change password" in {
      val program = for {
        auth   <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        result <- auth.changePassword(johnEmail, NewPasswordInfo(johnPassword, "newPassword"))
        isNicePassword <- result match {
          case Right(Some(user)) =>
            BCrypt.checkpwBool[IO]("newPassword", PasswordHash[BCrypt](user.hashedPassword))
          case Left(value) => IO.pure(false)
        }
      } yield isNicePassword

      program.asserting(_ shouldBe true)
    }

  }
}
