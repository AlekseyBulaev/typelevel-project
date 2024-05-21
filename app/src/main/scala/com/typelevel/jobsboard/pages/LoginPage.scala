package com.typelevel.jobsboard.pages

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import tyrian.http.*
import tyrian.cmds.Logger
import io.circe.generic.auto.*
import io.circe.syntax.*

import com.typelevel.jobsboard.common.*
import com.typelevel.jobsboard.domain.auth.*

/*
form
  - email
  - password
  - button
  - status (success or failure)
*/
final case class LoginPage(
                            email: String = "",
                            password: String = "",
                            status: Option[Page.Status] = None
                          ) extends Page {

  import LoginPage.*

  override def initCmd: Cmd[IO, Page.Msg] = Cmd.None

  override def update(msg: Page.Msg): (Page, Cmd[IO, Page.Msg]) = msg match {
    case UpdateEmail(e) => (this.copy(email = e), Cmd.None)
    case UpdatePassword(p) => (this.copy(password = p), Cmd.None)
    case AttemptLoggin =>
      if (!email.matches(Constants.emailRegex)) (setErrorStatus("Invalid email"), Cmd.None)
      else if (password.isEmpty) (setErrorStatus("Email is empty"), Cmd.None)
      else (this, Commands.login(LoginInfo(email, password)))
    case LoginError(error) => (setErrorStatus(error), Cmd.None)
    case LoginSuccess(token) => (setSuccessStatus("Success!"), Logger.consoleLog[IO](s"I have token: $token"))
    case _ => (this, Cmd.None)
  }

  override def view(): Html[Page.Msg] =
    div(`class` := "form-section")(
      // title: Sign Up
      div(`class` := "top-section")(
        h1("Sign Up")
      ),
      // form
      form(
        name := "signup",
        `class` := "form",
        onEvent(
          "submit",
          e => {
            e.preventDefault()
            NoOp
          }
        )
      )(
        renderInput("Email", "email", "text", true, UpdateEmail(_)),
        renderInput("Password", "password", "password", true, UpdatePassword(_)),
        button(`type` := "button", onClick(AttemptLoggin))("Log In")
      ),
      status.map(s => div(s.message)).getOrElse(div())
    )

  ///////////////////////////////////////////////////////////////////////////////////
  ///////  private
  ///////////////////////////////////////////////////////////////////////////////////

  // UI
  private def renderInput(
                           name: String,
                           uid: String,
                           kind: String,
                           isRequired: Boolean,
                           onChange: String => Msg
                         ) =
    div(`class` := "form-input")(
      label(`for` := name, `class` := "form-label")(
        if (isRequired) span("*") else span(),
        text(name)
      ),
      input(`type` := kind, `class` := "form-control", id := uid, onInput(onChange))
    )

  // util
  def setErrorStatus(message: String): Page = this.copy(status = Some(Page.Status(message = message, kind = Page.StatusKind.ERROR)))

  def setSuccessStatus(message: String): Page = this.copy(status = Some(Page.Status(message = message, kind = Page.StatusKind.SUCCESS)))

}

object LoginPage {
  trait Msg extends Page.Msg

  case class UpdateEmail(email: String) extends Msg

  case class UpdatePassword(password: String) extends Msg

  // actions
  case object NoOp extends Msg

  case object AttemptLoggin extends Msg

  // results
  case class LoginError(error: String) extends Msg

  case class LoginSuccess(token: String) extends Msg

  object Endpoints {
    val login: Endpoint[Msg] = new Endpoint[Msg] {
      override val location: String = Constants.Endoints.login
      override val method: Method = Method.Post
      override val onError: HttpError => Msg =
        e => LoginError(e.toString)
      override val onSuccess: Response => Msg = response =>
        val maybeToken = response.headers.get("authorization")
        maybeToken match
          case Some(token) => LoginSuccess(token)
          case None => LoginError("Invalid username or password")
    }
  }

  object Commands {
    def login(loginInfo: LoginInfo): Cmd[IO, Msg] = Endpoints.login.call(loginInfo)
  }
}