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

class AuthRoutes[F[_]: Concurrent: Logger] private (auth: Auth[F]) extends HttpValidationDsl[F] {
  private val authenticator = auth.authenticator
  private val securedHandler: SecuredRequestHandler[F, String, User, JwtToken] =
    SecuredRequestHandler(authenticator)
  // POST /auth/login { LoginInfo } => 200 Ok with Authorisation bearer {jwt}
  private val loginRoute: HttpRoutes[F] = HttpRoutes.of[F] { case req @ POST -> Root / "login" =>
    val maybeJwtToken = for {
      loginInfo  <- req.as[LoginInfo]
      maybeToken <- auth.login(loginInfo.email, loginInfo.password)
      _          <- Logger[F].info(s"User logging in ${loginInfo.email}")
    } yield maybeToken

    maybeJwtToken.map {
      case Some(token) => authenticator.embed(Response(Status.Ok), token)
      case None        => Response(Status.Unauthorized)
    }
  }

  // POST /auth/users { NewUserInfo } => 201 Created
  private val createUserRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "users" =>
      for {
        newUserInfo  <- req.as[NewUserInfo]
        maybeNewUser <- auth.signUp(newUserInfo)
        response <- maybeNewUser match
          case Some(user) => Created(user.email)
          case None       => BadRequest(s"User with email ${newUserInfo.email} already exists")
      } yield response
  }

  // PUT /auth/users/password { NewPasswordInfo } { Authorisation: Bearer {jwt} } => 200 Ok
  private val changePasswordRoute: AuthRoute[F] = {
    case req @ PUT -> Root / "users" / "password" asAuthed user =>
      for {
        newPasswordInfo  <- req.request.as[NewPasswordInfo]
        maybeUserOrError <- auth.changePassword(user.email, newPasswordInfo)
        resp <- maybeUserOrError match {
          case Right(Some(_)) => Ok()
          case Right(None)    => NotFound(FailureResponse(s"User ${user.email} not found"))
          case Left(_)        => Forbidden()
        }
      } yield resp
  }

  // PUT /auth/logout { Authorisation: Bearer {jwt} } => 200 Ok
  private val logoutRoute: AuthRoute[F] = { case req @ POST -> Root / "logout" asAuthed _ =>
    val token = req.authenticator
    for {
      _    <- authenticator.discard(token)
      resp <- Ok()
    } yield resp
  }

  private val unAuthedRoutes = loginRoute <+> createUserRoute
  private val authedRoutes =
    securedHandler.liftService(TSecAuthService(changePasswordRoute.orElse(logoutRoute)))
  val routes: HttpRoutes[F] = Router(
    "/auth" -> (
      unAuthedRoutes <+> authedRoutes
    )
  )
}
object AuthRoutes {
  def apply[F[_]: Concurrent: Logger](auth: Auth[F]) = new AuthRoutes[F](auth)
}
