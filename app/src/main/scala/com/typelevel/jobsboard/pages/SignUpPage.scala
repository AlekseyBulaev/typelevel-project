package com.typelevel.jobsboard.pages

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import tyrian.http.*
import tyrian.cmds.Logger
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.generic.auto.*
import com.typelevel.jobsboard.common.*
import com.typelevel.jobsboard.domain.auth.*

import com.typelevel.jobsboard.*
import java.net.http.HttpResponse

// form
/*
    input
      - email
      - password
      - confirm password
      - first name
      - last name
      - company
    button to trigger a sign up
 */
final case class SignUpPage(
                             email: String = "",
                             password: String = "",
                             confirmPassword: String = "",
                             firstName: String = "",
                             lastName: String = "",
                             company: String = "",
                             status: Option[Page.Status] = None,
                           ) extends Page {

  import SignUpPage.*

  override def initCmd: Cmd[IO, App.Msg] = Cmd.None // TODO

  override def update(msg: App.Msg): (Page, Cmd[IO, App.Msg]) = msg match {
    case UpdateEmail(e) => (this.copy(email = e), Cmd.None)
    case UpdatePassword(p) => (this.copy(password = p), Cmd.None)
    case UpdateConfirmPassword(cp) => (this.copy(confirmPassword = cp), Cmd.None)
    case UpdateFirstName(fn) => (this.copy(firstName = fn), Cmd.None)
    case UpdateLastName(ln) => (this.copy(lastName = ln), Cmd.None)
    case UpdateCompany(c) => (this.copy(company = c), Cmd.None)
    case AttemptSignUp =>
      if (!email.matches(Constants.emailRegex)) (setErrorStatus("Email is invalid"), Cmd.None)
      else if (password.isEmpty) (setErrorStatus("Please enter a password"), Cmd.None)
      else if (password != confirmPassword) (setErrorStatus("Password fields do not match"), Cmd.None)
      else (this, Commands.signUp(NewUserInfo(
        email,
        password,
        Option(firstName).filter(_.nonEmpty),
        Option(lastName).filter(_.nonEmpty),
        Option(company).filter(_.nonEmpty)
      )))
    case SignUpError(msg) => (setErrorStatus(msg), Cmd.None)
    case SignUpSuccess(msg) => (setSuccessStatus(msg), Cmd.None)
    case _ => (this, Cmd.None)
  }

  override def view(): Html[App.Msg] =
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
        // 6 inputs
        renderInput("Email", "email", "text", true, UpdateEmail(_)),
        renderInput("Password", "password", "password", true, UpdatePassword(_)),
        renderInput("Confirm Password", "cpassword", "password", true, UpdateConfirmPassword(_)),
        renderInput("First Name", "firstname", "text", true, UpdateFirstName(_)),
        renderInput("Last Name", "lastname", "text", true, UpdateLastName(_)),
        renderInput("Company", "company", "text", true, UpdateCompany(_)),
        // button
        button(`type` := "button", onClick(AttemptSignUp))("Sign Up")
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

object SignUpPage {
  trait Msg extends App.Msg

  case class UpdateEmail(email: String) extends Msg

  case class UpdatePassword(password: String) extends Msg

  case class UpdateConfirmPassword(confirmPassword: String) extends Msg

  case class UpdateFirstName(firstName: String) extends Msg

  case class UpdateLastName(lastName: String) extends Msg

  case class UpdateCompany(company: String) extends Msg

  // actions
  case object AttemptSignUp extends Msg

  case object NoOp extends Msg

  // statuses
  case class SignUpError(message: String) extends Msg

  case class SignUpSuccess(message: String) extends Msg

  object Endpoints {
    val signup: Endpoint[Msg] = new Endpoint[Msg] {
      override val location: String = Constants.endpoints.signup
      override val method: Method = Method.Post
      override val onSuccess: Response => Msg = response => response.status match {
        case Status(201, _) => SignUpSuccess("Success!")
        case Status(s, _) if s >= 400 && s < 500 =>
          val json = response.body
          val parsed = parse(json).flatMap(_.hcursor.get[String]("error"))
          parsed match
            case Left(e) => SignUpError(s"Error: ${e.getMessage}")
            case Right(e) => SignUpError(e)
      }

      override val onError: HttpError => Msg =
        e => SignUpError(e.toString)
    }
  }

  object Commands {
    def signUp(newUserInfo: NewUserInfo): Cmd[IO, Msg] =
      Endpoints.signup.internalCall(newUserInfo)
  }

}
