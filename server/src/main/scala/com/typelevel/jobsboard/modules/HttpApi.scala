package com.typelevel.jobsboard.modules

import cats.effect.*
import cats.implicits.*
import cats.data.*
import com.typelevel.jobsboard.config.SecurityConfig
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*
import org.typelevel.log4cats.Logger
import tsec.authentication.{AugmentedJWT, BackingStore, IdentityStore, JWTAuthenticator, SecuredRequestHandler}
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.jca.BCrypt
import tsec.passwordhashers.PasswordHash
import tsec.common.SecureRandomId
import com.typelevel.jobsboard.http.routes.*
import com.typelevel.jobsboard.config.*
import com.typelevel.jobsboard.domain.security.*
import com.typelevel.jobsboard.domain.user.*
import com.typelevel.jobsboard.core.*

class HttpApi[F[_]: Concurrent: Logger] private (core: Core[F], authenticator: Authenticator[F]) {
  given securedHandler: SecuredHandler[F] = SecuredRequestHandler(authenticator)
  private val healthRoutes = HealthRoutes[F].routes
  private val jobRoutes    = JobRoutes[F](core.jobs).routes
  private val authRoutes   = AuthRoutes[F](core.auth, authenticator).routes

  val endpoints: HttpRoutes[F] = Router("/api" -> (healthRoutes <+> jobRoutes <+> authRoutes))
}

object HttpApi {

  def createAuthenticator[F[_]: Sync](
      users: Users[F],
      securityConfig: SecurityConfig
  ): F[Authenticator[F]] = {
    // 1. identity store: String => Option[F, User]
    val idStore: IdentityStore[F, String, User] = (email: String) => OptionT(users.find(email))

    // 2. backing store for JWT tokens: BackingStore[F, id, JwtToken]
    val tokenStoreF = Ref.of[F, Map[SecureRandomId, JwtToken]](Map.empty).map { ref =>
      new BackingStore[F, SecureRandomId, JwtToken] {
        override def get(id: SecureRandomId): OptionT[F, JwtToken] = OptionT(ref.get.map(_.get(id)))

        override def put(elem: JwtToken): F[JwtToken] =
          ref.modify(store => (store + (elem.id -> elem), elem))

        override def update(v: JwtToken): F[JwtToken] = put(v)

        override def delete(id: SecureRandomId): F[Unit] = ref.modify(store => (store - id, ()))
      }
    }

    // 3. hashing key
    val keyF = HMACSHA256.buildKey[F](securityConfig.secret.getBytes("UTF-8"))

    for {
      key        <- keyF
      tokenStore <- tokenStoreF
      // 4. authenticator
    } yield JWTAuthenticator.backed.inBearerToken(
      expiryDuration = securityConfig.jwtExpiryDuration,
      maxIdle = None,
      identityStore = idStore,
      tokenStore = tokenStore,
      signingKey = key
    )
  }
  def apply[F[_]: Async: Logger](core: Core[F], securityConfig: SecurityConfig): Resource[F, HttpApi[F]] =
    Resource.eval(createAuthenticator[F](core.users, securityConfig)
      .map(authenticator => new HttpApi[F](core, authenticator)))
}
