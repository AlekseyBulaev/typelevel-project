package com.typelevel.jobsboard.http.routes

import cats.data.*
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
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.typelevel.jobsboard.fixtures.*
import com.typelevel.jobsboard.core.*
import com.typelevel.jobsboard.http.routes.*
import com.typelevel.Application.Logger
import com.typelevel.jobsboard.domain.auth.{LoginInfo, NewPasswordInfo}
import com.typelevel.jobsboard.domain.security.{Authenticator, JwtToken}
import com.typelevel.jobsboard.domain.user.User
import com.typelevel.jobsboard.domain.{auth, user}
import org.http4s.headers.Authorization
import org.typelevel.ci.CIStringSyntax
import tsec.authentication.{IdentityStore, JWTAuthenticator}
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

import scala.concurrent.duration.*

class AuthRoutesSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Http4sDsl[IO]
    with SecuredRouteFixture
    with UserFixture {
  //////////////////////////////////////////////////////////////////////////////////////////////
  // prep
  //////////////////////////////////////////////////////////////////////////////////////////////
  val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val mockedAuth: Auth[IO] = new Auth[IO] {
    override def login(email: String, password: String): IO[Option[User]] =
      if (email == johnEmail && password == johnPassword)
        IO(Some(John))
      else IO.pure(None)

    override def signUp(newUserInfo: user.NewUserInfo): IO[Option[user.User]] =
      if (newUserInfo.email == johnEmail)
        IO.pure(None)
      else
        IO.pure(Some(John))

    override def changePassword(
        email: String,
        newPasswordInfo: auth.NewPasswordInfo
    ): IO[Either[String, Option[user.User]]] =
      if (email == johnEmail)
        if (newPasswordInfo.oldPassword == johnPassword)
          IO.pure(Right(Some(John)))
        else IO.pure(Left("Invalid password"))
      else IO.pure(Right(None))

    override def delete(email: String): IO[Boolean] = IO.pure(true)
  }

  val authRoutes: HttpRoutes[IO] = AuthRoutes[IO](mockedAuth, mockedAuthenticator).routes

  //////////////////////////////////////////////////////////////////////////////////////////////
  // tests
  //////////////////////////////////////////////////////////////////////////////////////////////

  "AuthRoutes" - {
    "Should return a 401 - unauthorized if login fails" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/login")
            .withEntity(LoginInfo(johnEmail, "wrongPassword"))
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    "Should return a 200 - OK + jwt if login is successful" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/login")
            .withEntity(LoginInfo(johnEmail, johnPassword))
        )
      } yield {
        response.status shouldBe Status.Ok
        response.headers.get(ci"Authorization") shouldBe defined
      }
    }

    "Should return a 400 - BadRequest if the user to create already exists" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/users")
            .withEntity(existingUser)
        )
      } yield {
        response.status shouldBe Status.BadRequest
      }
    }

    "Should return a 201 - Created if the user creation succeeds" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/users")
            .withEntity(newUser)
        )
      } yield {
        response.status shouldBe Status.Created
      }
    }

    "Should return a 200 - Ok if the logging out with a jwt token" in {
      for {
        jwtToken <- mockedAuthenticator.create(billEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/logout")
            .withBearerToken(jwtToken)
        )
      } yield {
        response.status shouldBe Status.Ok
      }
    }

    "Should return a 401 - Unauthorized if the logging out without a valid jwt token" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/logout")
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    "Should return a 404 - Not Found if changing password for user that does not exist" in {
      for {
        jwtToken <- mockedAuthenticator.create(billEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withBearerToken(jwtToken)
            .withEntity(NewPasswordInfo(billPassword, "newPassword"))
        )
      } yield {
        response.status shouldBe Status.NotFound
      }
    }

    "Should return a 403 - Forbidden if the old password is incorrect" in {
      for {
        jwtToken <- mockedAuthenticator.create(johnEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withBearerToken(jwtToken)
            .withEntity(NewPasswordInfo("wrongPassword", "newPassword"))
        )
      } yield {
        response.status shouldBe Status.Forbidden
      }
    }

    "Should return a 401 - Unauthorized if a jwt is invalid" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withEntity(NewPasswordInfo(johnPassword, "newPassword"))
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    "Should return a 200 - OK if changing password for a user with valid jwt and password" in {
      for {
        jwtToken <- mockedAuthenticator.create(johnEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withBearerToken(jwtToken)
            .withEntity(NewPasswordInfo(johnPassword, "newPassword"))
        )
      } yield {
        response.status shouldBe Status.Ok
      }
    }

    "Should return a 401 - Unauthorized if a non admin tries to delete the user" in {
      for {
        jwtToken <- mockedAuthenticator.create(billEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/auth/users/email@mail.com")
            .withBearerToken(jwtToken)
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    "Should return a 200 - OK if an admin tries to delete the user" in {
      for {
        jwtToken <- mockedAuthenticator.create(johnEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/auth/users/email@mail.com")
            .withBearerToken(jwtToken)
        )
      } yield {
        response.status shouldBe Status.Ok
      }
    }
  }
}
