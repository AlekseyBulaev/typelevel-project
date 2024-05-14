package com.typelevel.jobsboard.fixtures

import cats.data.*
import cats.effect.*
import tsec.authentication.{IdentityStore, JWTAuthenticator}
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256
import com.typelevel.jobsboard.domain.user.*
import com.typelevel.jobsboard.domain.security.*
import org.http4s.{AuthScheme, Credentials, Request}
import org.http4s.headers.Authorization

import scala.concurrent.duration.*
trait SecuredRouteFixture extends UserFixture {
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

  extension (r: Request[IO])
    def withBearerToken(a: JwtToken): Request[IO] = r.putHeaders {
      val jwtString = JWTMac.toEncodedString[IO, HMACSHA256](a.jwt)
      Authorization(Credentials.Token(AuthScheme.Bearer, jwtString))
    }
}
