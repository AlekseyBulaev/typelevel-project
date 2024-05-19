package com.typelevel.jobsboard.core

import cats.data.*
import cats.effect.*
import org.scalatest.freespec.AsyncFreeSpec
import cats.effect.testing.scalatest.AsyncIOSpec
import com.typelevel.jobsboard.config.SecurityConfig
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

  val mockedSecurityConfig: SecurityConfig = SecurityConfig("secret", 1.day)

  val mockedTokens: Tokens[IO] = new Tokens[IO] {
    override def getToken(email: String): IO[Option[String]] = {
      if (email == johnEmail) IO.pure(Some("abc123"))
      else IO.pure(None)
    }

    override def checkToken(email: String, token: String): IO[Boolean] = IO.pure(token == "abc123")
  }

  val mockedEmails: Emails[IO] = new Emails[IO] {
    override def sendEmail(to: String, subject: String, content: String): IO[Unit] = IO.unit

    override def sendPasswordRecoveryEmail(to: String, token: String): IO[Unit] = IO.unit
  }

  def probedEmails(users: Ref[IO, Set[String]]): Emails[IO] = new Emails[IO] {
    override def sendEmail(to: String, subject: String, content: String): IO[Unit] =
      users.modify(set => (set + to, ()))

    override def sendPasswordRecoveryEmail(to: String, token: String): IO[Unit] =
      sendEmail(to, "Your token", "token")
  }

  "Auth 'algebra'" - {
    "login should return None if the user doesn't exist" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
        maybeToken <- auth.login(newUser.email, "password")
      } yield maybeToken

      program.asserting(_ shouldBe None)
    }

    "login should return None if the user exists with the wrong password" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
        maybeToken <- auth.login(johnEmail, "wrongPassword")
      } yield maybeToken

      program.asserting(_ shouldBe None)
    }

    "login should return a token if the user exists and the password is correct" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
        maybeToken <- auth.login(johnEmail, johnPassword)
      } yield maybeToken

      program.asserting(_ shouldBe defined)
    }

    "signingUp should not create a user with an existing email" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
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
        auth <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
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
        auth <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
        maybeUser <- auth.changePassword(
          newUser.email,
          NewPasswordInfo("somePassword", "newPassword")
        )
      } yield maybeUser

      program.asserting(_ shouldBe Right(None))
    }

    "changePassword should return Left with an error if the user exists and the password is incorrect" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
        maybeUser <- auth.changePassword(
          John.email,
          NewPasswordInfo("somePass", "somePass")
        )
      } yield maybeUser

      program.asserting(_ shouldBe Left("Invalid password"))
    }

    "changePassword should correctly change password" in {
      val program = for {
        auth   <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
        result <- auth.changePassword(johnEmail, NewPasswordInfo(johnPassword, "newPassword"))
        isNicePassword <- result match {
          case Right(Some(user)) =>
            BCrypt.checkpwBool[IO]("newPassword", PasswordHash[BCrypt](user.hashedPassword))
          case Left(value) => IO.pure(false)
        }
      } yield isNicePassword

      program.asserting(_ shouldBe true)
    }

    "recoverPassword should fail for a user that does not exist, even if the token is correct" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
        result1 <- auth.recoverPasswordFromToken(
          "notexist@mail.com",
          "abc123",
          "newPassword"
        )
        result2 <- auth.recoverPasswordFromToken(
          "notexist@mail.com",
          "invalidToken",
          "newPassword"
        )
      } yield (result1, result2)

      program.asserting(_ shouldBe (false, false))
    }

    "recoverPassword should fail for a user that exists, but the token is wrong" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
        result <- auth.recoverPasswordFromToken(
          johnEmail,
          "invalidToken",
          "newPassword"
        )
      } yield result
      program.asserting(_ shouldBe false)
    }

    "recoverPassword should succeeded for a correct combination user/token" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedTokens, mockedEmails)
        result <- auth.recoverPasswordFromToken(
          johnEmail,
          "abc123",
          "newPassword"
        )
      } yield result
      program.asserting(_ shouldBe true)
    }

    "sending recovery passwords should fail if the user doesn't exist" in {
      val program = for {
        set                  <- Ref.of[IO, Set[String]](Set())
        emails               <- IO(probedEmails(set))
        auth                 <- LiveAuth[IO](mockedUsers, mockedTokens, emails)
        result               <- auth.sendPasswordRecoveryToken("notExists@mail.com")
        usersBeingSentEmails <- set.get
      } yield usersBeingSentEmails
      program.asserting(_ shouldBe empty)
    }

    "sending recovery passwords should succeeded if the user exists " in {
      val program = for {
        set                  <- Ref.of[IO, Set[String]](Set())
        emails               <- IO(probedEmails(set))
        auth                 <- LiveAuth[IO](mockedUsers, mockedTokens, emails)
        result               <- auth.sendPasswordRecoveryToken(johnEmail)
        usersBeingSentEmails <- set.get
      } yield usersBeingSentEmails
      program.asserting(_ should contain(johnEmail))
    }

  }
}
