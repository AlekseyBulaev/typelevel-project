package com.typelevel.jobsboard.domain

import com.typelevel.jobsboard.domain.user.*
import tsec.authentication.{AugmentedJWT, JWTAuthenticator}
import tsec.mac.jca.HMACSHA256

object security {
  type Crypto = HMACSHA256
  type JwtToken             = AugmentedJWT[Crypto, String]
  type Authenticator[F[_]] = JWTAuthenticator[F, String, User, Crypto]
}
