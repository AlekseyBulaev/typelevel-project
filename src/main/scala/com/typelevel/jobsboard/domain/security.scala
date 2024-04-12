package com.typelevel.jobsboard.domain

import cats.*
import cats.implicits.*
import tsec.authentication.{AugmentedJWT, JWTAuthenticator, SecuredRequest, TSecAuthService}
import tsec.mac.jca.HMACSHA256
import org.http4s.{Response, Status}
import com.typelevel.jobsboard.domain.user.*
import com.typelevel.jobsboard.domain.*
import tsec.authorization.{AuthorizationInfo, BasicRBAC}

object security {
  type Crypto              = HMACSHA256
  type JwtToken            = AugmentedJWT[Crypto, String]
  type Authenticator[F[_]] = JWTAuthenticator[F, String, User, Crypto]
  type AuthRoute[F[_]]     = PartialFunction[SecuredRequest[F, User, JwtToken], F[Response[F]]]
  type AuthRBAC[F[_]]      = BasicRBAC[F, Role, User, JwtToken]

  given authRole[F[_]: Applicative]: AuthorizationInfo[F, Role, User] with {
    override def fetchInfo(u: User): F[Role] = u.role.pure[F]
  }
  def allRoles[F[_]: MonadThrow]: AuthRBAC[F] =
    BasicRBAC.all[F, Role, User, JwtToken]
  def recruiterOnly[F[_]: MonadThrow]: AuthRBAC[F] = BasicRBAC(Role.RECRUITER)
  def adminOnly[F[_]: MonadThrow]: AuthRBAC[F]     = BasicRBAC(Role.ADMIN)

  case class Authorizations[F[_]](rbacRouts: Map[AuthRBAC[F], List[AuthRoute[F]]])
  object Authorizations {
    given combiner[F[_]]: Semigroup[Authorizations[F]] = Semigroup.instance { (authA, authB) =>
      Authorizations(authA.rbacRouts |+| authB.rbacRouts)
    }
  }
  extension [F[_]](authRoute: AuthRoute[F]) {
    def restrictedTo(rbac: AuthRBAC[F]): Authorizations[F] = Authorizations(
      Map(rbac -> List(authRoute))
    )
  }

  given auth2tsec[F[_]: Monad]: Conversion[Authorizations[F], TSecAuthService[User, JwtToken, F]] =
    authz => {
      val unauthorizedService: TSecAuthService[User, JwtToken, F] =
        TSecAuthService[User, JwtToken, F] { _ =>
          Response[F](Status.Unauthorized).pure[F]
        }

      authz.rbacRouts.toSeq
        .foldLeft(unauthorizedService) { case (acc, (rbac, routes)) =>
          val bigRoute = routes.reduce(_.orElse(_))
          TSecAuthService.withAuthorizationHandler(rbac)(bigRoute, acc.run)
        }
    }

}
