package com.typelevel.jobsboard.http.routes

import cats.effect.*
import cats.implicits.*
import org.typelevel.log4cats.Logger
import org.http4s.HttpRoutes
import org.http4s.server.Router

import com.typelevel.jobsboard.core.*
import com.typelevel.jobsboard.http.validation.syntax.HttpValidationDsl

class AuthRoutes[F[_]: Concurrent: Logger] private (auth: Auth[F]) extends HttpValidationDsl[F] {
  // POST /auth/login { LoginInfo } => 200 Ok with Authorisation bearer {jwt}
  private val loginRoute: HttpRoutes[F] = HttpRoutes.of[F] { case POST -> Root / "login" => Ok("TODO") }

  // POST /auth/users { NewUserInfo } => 201 Created
  private val createUserRoute: HttpRoutes[F] = HttpRoutes.of[F] { case POST -> Root / "users" => Ok("TODO") }

  // PUT /auth/users/password { NewPasswordInfo } { Authorisation: Bearer {jwt} } => 200 Ok
  private val changePasswordRoute: HttpRoutes[F] = HttpRoutes.of[F] { case PUT -> Root / "users" / "password" => Ok("TODO") }

  // PUT /auth/logout { Authorisation: Bearer {jwt} } => 200 Ok
  private val logoutRoute: HttpRoutes[F] = HttpRoutes.of[F] { case PUT -> Root / "logout" => Ok("TODO") }

  val routes: HttpRoutes[F] = Router(
    "/auth" -> (
      loginRoute <+> createUserRoute <+> changePasswordRoute <+> logoutRoute
    )
  )
}
object AuthRoutes {
  def apply[F[_]: Concurrent: Logger](auth: Auth[F]) = new AuthRoutes[F](auth)
}
