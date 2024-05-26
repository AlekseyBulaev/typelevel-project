package com.typelevel.jobsboard.core

import tyrian.*
import cats.effect.IO
import tyrian.http.*
import tyrian.cmds.Logger
import org.scalajs.dom.document

import scala.scalajs.js.{Date, native}
import com.typelevel.jobsboard.*
import com.typelevel.jobsboard.common.*
import com.typelevel.jobsboard.pages.*


case class Session(email: Option[String] = None, token: Option[String] = None) {

  import Session.*

  def update(msg: Msg): (Session, Cmd[IO, App.Msg]) = msg match
    case SetToken(e, t, isNewUser) =>
      val cookieCmd = Commands.setAllSessionCookies(e, t, isNewUser)
      val routingCmd = if (isNewUser)
        Cmd.Emit(Router.ChangeLocation(Page.Urls.HOME)) // new user
      else
      // check whether the token is still valid on the server
        Commands.checkToken
      (this.copy(Some(e), Some(t)), cookieCmd |+| routingCmd)
    // check token action
    case CheckToken => (this, Commands.checkToken)
    case KeepToken => (this, Cmd.None)
    // logout action
    case Logout =>
      // trigger an AUTHORIZED http request
      val cmd = token.map(_ => Commands.logout).getOrElse(Cmd.None)
      (this, cmd)
    case LogoutSuccess | InvalidateToken =>
      (this.copy(email = None, token = None), Commands.clearAllSessionCookies() |+| Cmd.Emit(Router.ChangeLocation(Page.Urls.HOME)))

  def initCmd: Cmd[IO, Msg] = {
    val maybeCommand = for {
      email <- getCookie(Constants.cookies.email)
      token <- getCookie(Constants.cookies.token)
    } yield Cmd.Emit(SetToken(email, token, isNewUser = false))

    maybeCommand.getOrElse(Cmd.None)
  }
}

object Session {
  trait Msg extends App.Msg

  case class SetToken(email: String, token: String, isNewUser: Boolean = false) extends Msg

  // check the token
  case object CheckToken extends Msg

  case object KeepToken extends Msg

  case object InvalidateToken extends Msg

  // logout action
  case object Logout extends Msg

  case object LogoutSuccess extends Msg

  case object LogoutFailure extends Msg

  def isActive: Boolean = getUserToken().nonEmpty

  def getUserToken(): Option[String] = getCookie(Constants.cookies.token)

  object Endpoints {
    val logout: Endpoint[Msg] = new Endpoint[Msg] {
      override val location: String = Constants.endpoints.logout
      override val method: Method = Method.Post
      override val onSuccess: Response => Msg = _ => LogoutSuccess
      override val onError: HttpError => Msg = _ => LogoutFailure
    }

    val checkToken = new Endpoint[Msg] {
      override val location: String = Constants.endpoints.checkToken
      override val method: Method = Method.Get
      override val onSuccess: Response => Msg = response => response.status match {
        case Status(200, _) => KeepToken
        case _ => InvalidateToken
      }
      override val onError: HttpError => Msg = _ => InvalidateToken
    }
  }

  object Commands {

    def logout: Cmd[IO, Msg] = Endpoints.logout.callAuthorized()

    def checkToken: Cmd[IO, Msg] = Endpoints.checkToken.callAuthorized()

    def setSessionCookie(name: String, value: String, isFresh: Boolean = false): Cmd[IO, Msg] =
      Cmd.SideEffect[IO] {
        if (getCookie(name).isEmpty || isFresh)
          document.cookie = s"$name=$value;expires=${new Date(Date.now() + Constants.cookies.duration)};path=/"
      }

    def setAllSessionCookies(email: String, token: String, isFresh: Boolean = false): Cmd[IO, Msg] =
      setSessionCookie(Constants.cookies.email, email, isFresh) |+|
        setSessionCookie(Constants.cookies.token, token, isFresh)

    def clearSessionCookie(name: String): Cmd[IO, Msg] =
      Cmd.SideEffect[IO] {
        document.cookie = s"$name=;expires=${new Date(0)};[ath=/"
      }

    def clearAllSessionCookies(): Cmd[IO, Msg] =
      clearSessionCookie(Constants.cookies.email) |+|
        clearSessionCookie(Constants.cookies.token)
  }

  private def getCookie(name: String): Option[String] = document.cookie
    .split(";")
    .map(_.trim)
    .find(_.startsWith(s"$name"))
    .map(_.split("="))
    .map(_(1))
}
