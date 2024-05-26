package com.typelevel.jobsboard.http.routes

import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import cats.effect.*
import cats.implicits.*
import org.typelevel.log4cats.Logger
import org.http4s.server.Router
import org.http4s.{HttpRoutes, Response, Status}
import tsec.authentication.{SecuredRequestHandler, TSecAuthService, asAuthed}
import com.typelevel.jobsboard.http.responses.*
import com.typelevel.jobsboard.http.validation.syntax.*
import com.typelevel.jobsboard.core.*
import com.typelevel.jobsboard.domain.auth.*
import com.typelevel.jobsboard.domain.security.*
import com.typelevel.jobsboard.domain.user.*

import scala.language.implicitConversions

class AuthRoutes[F[_] : Concurrent : Logger : SecuredHandler] private(
                                                                       auth: Auth[F],
                                                                       authenticator: Authenticator[F]
                                                                     ) extends HttpValidationDsl[F] {

  // POST /auth/login { LoginInfo } => 200 Ok with Authorisation bearer {jwt}
  private val loginRoute: HttpRoutes[F] = HttpRoutes.of[F] { case req@POST -> Root / "login" =>
    req.validate[LoginInfo] { loginInfo =>
      val maybeJwtToken = for {
        maybeUser <- auth.login(loginInfo.email, loginInfo.password)
        _ <- Logger[F].info(s"User logging in ${loginInfo.email}")
        maybeToken <- maybeUser.traverse(user => authenticator.create(user.email))
      } yield maybeToken

      maybeJwtToken.map {
        case Some(token) => authenticator.embed(Response(Status.Ok), token)
        case None => Response(Status.Unauthorized)
      }
    }
  }

  // POST /auth/users { NewUserInfo } => 201 Created
  private val createUserRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req@POST -> Root / "users" =>
      req.validate[NewUserInfo] { newUserInfo =>
        for {
          maybeNewUser <- auth.signUp(newUserInfo)
          response <- maybeNewUser match
            case Some(user) => Created(user.email)
            case None => BadRequest(FailureResponse(s"User with email ${newUserInfo.email} already exists"))
        } yield response
      }
  }

  // PUT /auth/users/password { NewPasswordInfo } { Authorisation: Bearer {jwt} } => 200 Ok
  private val changePasswordRoute: AuthRoute[F] = {
    case req@PUT -> Root / "users" / "password" asAuthed user =>
      req.request.validate[NewPasswordInfo] { newPasswordInfo =>
        for {
          maybeUserOrError <- auth.changePassword(user.email, newPasswordInfo)
          resp <- maybeUserOrError match {
            case Right(Some(_)) => Ok()
            case Right(None) => NotFound(FailureResponse(s"User ${user.email} not found"))
            case Left(_) => Forbidden()
          }
        } yield resp
      }
  }

  // POST /auth/recover { RecoverPasswordInfo }
  private val recoverPasswordRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req@POST -> Root / "recover" => for {
      rpInfo <- req.as[RecoverPasswordInfo]
      recoverySuccessful <- auth.recoverPasswordFromToken(rpInfo.email, rpInfo.token, rpInfo.newPassword)
      resp <- if (recoverySuccessful) Ok() else Forbidden(FailureResponse("Email/token combination is incorrect"))
    } yield resp
  }

  // POST /auth/reset { ForgotPasswordInfo }
  private val forgotPasswordRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req@POST -> Root / "reset" => for {
      fpInfo <- req.as[ForgotPasswordInfo]
      _ <- auth.sendPasswordRecoveryToken(fpInfo.email)
      resp <- Ok()
    } yield resp
  }

  // PUT /auth/logout { Authorisation: Bearer {jwt} } => 200 Ok
  private val logoutRoute: AuthRoute[F] = {
    case req@POST -> Root / "logout" asAuthed _ =>
      val token = req.authenticator
      for {
        _ <- authenticator.discard(token)
        resp <- Ok()
      } yield resp
  }

  // DELETE /auth/users/mail@mail.com
  private val deleteUserRoute: AuthRoute[F] = {
    case req@DELETE -> Root / "users" / email asAuthed user =>
      auth.delete(email).flatMap {
        case true => Ok()
        case false => NotFound()
      }
  }

  private val checkTokenRoute: AuthRoute[F] = {
    case GET -> Root / "checkToken" asAuthed _ => Ok()
  }

  private val unAuthedRoutes = loginRoute <+> createUserRoute <+> forgotPasswordRoute <+> recoverPasswordRoute
  private val authedRoutes =
    SecuredHandler[F].liftService(
      checkTokenRoute.restrictedTo(allRoles) |+|
        changePasswordRoute.restrictedTo(allRoles) |+|
        logoutRoute.restrictedTo(allRoles) |+|
        deleteUserRoute.restrictedTo(adminOnly)
    )
  val routes: HttpRoutes[F] = Router(
    "/auth" -> (
      unAuthedRoutes <+> authedRoutes
      )
  )
}

object AuthRoutes {
  def apply[F[_] : Concurrent : Logger : SecuredHandler](
                                                          auth: Auth[F],
                                                          authenticator: Authenticator[F]
                                                        ) = new AuthRoutes[F](auth, authenticator)
}
