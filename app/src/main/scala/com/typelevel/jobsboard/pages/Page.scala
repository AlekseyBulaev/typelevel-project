package com.typelevel.jobsboard.pages

import cats.effect.*
import tyrian.*

import com.typelevel.jobsboard.pages.*
import com.typelevel.jobsboard.*

object Page {
  trait Msg extends App.Msg

  enum StatusKind {
    case SUCCESS, ERROR, LOADING
  }

  final case class Status(message: String, kind: StatusKind)

  object Urls {
    val LOGIN = "/login"
    val SIGNUP = "/signup"
    val FORGOT_PASSWORD = "/forgotpassword"
    val RECOVER_PASSWORD = "/recoverpassword"
    val EMPTY = ""
    val JOBS = "/jobs"
    val HOME = "/"
  }

  import Urls.*

  def get(location: String): Page = location match {
    case `LOGIN` => LoginPage()
    case `SIGNUP` => SignUpPage()
    case `FORGOT_PASSWORD` => ForgotPasswordPage()
    case `RECOVER_PASSWORD` => RecoverPasswordPage()
    case `EMPTY` | `JOBS` | `HOME` => JobListPage()
    case s"/jobs/$id" => JobPage(id)
    case _ => NotFoundPage()
  }

}

abstract class Page {

  import Page.*

  // API
  // send a command upon instantiating
  def initCmd: Cmd[IO, App.Msg]

  // update
  def update(msg: App.Msg): (Page, Cmd[IO, App.Msg])

  // render
  def view(): Html[App.Msg]
}

// login page
// sign up page
// recovery password page
// forgot password page
// job list page = home page
// individual job page
// not found page