package com.typelevel.jobsboard.pages

import cats.effect.*
import tyrian.*

import com.typelevel.jobsboard.pages.*
object Page {
  trait Msg

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
  def initCmd: Cmd[IO, Msg]

  // update
  def update(msg: Msg): (Page, Cmd[IO, Msg])

  // render
  def view(): Html[Msg]
}

// login page
// sign up page
// recovery password page
// forgot password page
// job list page = home page
// individual job page
// not found page