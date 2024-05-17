package com.typelevel.jobsboard.core

import cats.effect.*
import cats.effect.testing.scalatest.AsyncIOSpec
import com.typelevel.jobsboard.config.TokenConfig
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.*

import com.typelevel.jobsboard.fixtures.*
import com.typelevel.jobsboard.domain.user.*
import com.typelevel.jobsboard.config.*
class TokensSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with DoobieSpec
    with UserFixture {
  override val initScript: String = "sql/recoverytokens.sql"
  given Logger: Logger[IO]        = Slf4jLogger.getLogger[IO]

  "Tokens 'algebra'" - {
    "should not create a token for non-existing user" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(10000000L))
          token  <- tokens.getToken("notexist@mail.com")
        } yield token

        program.asserting(_ shouldBe None)
      }
    }

    "should create a token for an existing user" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(10000000L))
          token <- tokens.getToken(johnEmail)
        } yield token

        program.asserting(_ shouldBe defined)
      }
    }

    "should not validate expired token" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(100L))
          maybeToken <- tokens.getToken(johnEmail)
          _ <- IO.sleep(500.millis)
          isTokenValid <- maybeToken match
            case Some(token) => tokens.checkToken(johnEmail, token)
            case None => IO.pure(false)
        } yield isTokenValid

        program.asserting(_ shouldBe false)
      }
    }


    "should validate non-expired token" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(10000000L))
          maybeToken <- tokens.getToken(johnEmail)
          _ <- IO.sleep(500.millis)
          isTokenValid <- maybeToken match
            case Some(token) => tokens.checkToken(johnEmail, token)
            case None => IO.pure(false)
        } yield isTokenValid

        program.asserting(_ shouldBe true)
      }
    }

    "should only validate tokens for the user that generated them" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(10000000L))
          maybeToken <- tokens.getToken(johnEmail)
          isTokenValid <- maybeToken match
            case Some(token) => tokens.checkToken(johnEmail, token)
            case None => IO.pure(false)
          isAnotherValid <- maybeToken match
            case Some(token) => tokens.checkToken("another@mail.com", token)
            case None => IO.pure(false)
        } yield (isTokenValid, isAnotherValid)

        program.asserting {
          case (isTokenValid, isAnotherValid) =>
            isTokenValid shouldBe true
            isAnotherValid shouldBe false
        }
      }
    }
  }
}
