package com.typelevel.jobsboard.core

import cats.effect.*
import cats.implicits.*
import doobie.util.transactor.Transactor
import doobie.implicits.*
import org.typelevel.log4cats.Logger

import com.typelevel.jobsboard.config.TokenConfig
import scala.util.Random

trait Tokens[F[_]] {
  def getToken(email: String): F[Option[String]]
  def checkToken(email: String, token: String): F[Boolean]
}

class LiveTokens[F[_]: MonadCancelThrow: Logger](users: Users[F])(
    xa: Transactor[F],
    tokenConfig: TokenConfig
) extends Tokens[F] {
  override def getToken(email: String): F[Option[String]] =
    users.find(email).flatMap {
      case None    => None.pure[F]
      case Some(_) => getFreshToken(email).map(Some(_))
    }

  override def checkToken(email: String, token: String): F[Boolean] =
    sql"""
         SELECT token
         FROM recoverytokens
         WHERE email=$email
         AND expiration > ${System.currentTimeMillis()}
       """
      .query[String]
      .option
      .transact(xa)
      .map(_.nonEmpty)

  // private
  val tokenDuration: Long = tokenConfig.tokenDuration
  def randomToken(maxLength: Int): F[String] =
    Random.alphanumeric.map(Character.toUpperCase).take(maxLength).mkString.pure[F]
  def getFreshToken(email: String): F[String] =
    findToken(email).flatMap {
      case None    => generateToken(email)
      case Some(_) => updateToken(email)
    }
  def findToken(email: String): F[Option[String]] =
    sql"SELECT token FROM recoverytokens WHERE email = $email"
      .query[String]
      .option
      .transact(xa)

  def generateToken(email: String): F[String] = for {
    token <- randomToken(8)
    _ <-
      sql"""INSERT INTO recoverytokens(email, token, expiration)
           VALUES ($email, $token, ${System.currentTimeMillis() + tokenDuration})
           """.update.run.transact(xa)
  } yield token

  def updateToken(email: String): F[String] = for {
    token <- randomToken(8)
    _ <-
      sql"""UPDATE recoverytokens
            SET token=$token, expiration=${System.currentTimeMillis() + tokenDuration}
           WHERE email=$email
           """.update.run.transact(xa)
  } yield token
}

object LiveTokens {
  def apply[F[_]: MonadCancelThrow: Logger](
      users: Users[F]
  )(xa: Transactor[F], tokenConfig: TokenConfig): F[LiveTokens[F]] =
    new LiveTokens[F](users)(xa, tokenConfig).pure[F]
}
