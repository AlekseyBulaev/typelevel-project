package com.typelevel.jobsboard

import tyrian.*
import tyrian.Html.*
import cats.effect.IO

import scala.scalajs.js.annotation.*
import org.scalajs.dom.{console, window}
import tyrian.cmds.Logger

import scala.concurrent.duration.*

import core.*

object App {
  type Msg = Router.Msg
  case class Model(router: Router) extends Msg
}
@JSExportTopLevel("JobsBoardApp")
class App extends TyrianApp[App.Msg, App.Model] {
  import App.*

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) = {
    val (router, cmd) = Router.startAt(window.location.pathname)
    (Model(router), cmd)
  }

  override def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.make( // listener for the browser history changes
      "urlChange",
      model.router.history.state.discrete
        .map(_.get)
        .map(newLocation => Router.ChangeLocation(newLocation, true))
    )

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = { case msg: Router.Msg =>
    val (newRouter, cmd) = model.router.update(msg)
    (model.copy(router = newRouter), cmd)
  }

  override def view(model: Model): Html[Msg] =
    div(
      renderNavLink("Jobs", "/jobs"),
      renderNavLink("Login", "/login"),
      renderNavLink("Sign up", "/signup"),
      div(s"you are now at location: ${model.router.location}")
    )

  private def renderNavLink(text: String, location: String) =
    // header <a href="/jobs">Jobs</a>
    a(
      href    := location,
      `class` := "nav-link",
      onEvent(
        "click",
        e => {
          e.preventDefault() // native JS - prevent reloading the page
          Router.ChangeLocation(location)
        }
      )
    )(text)
}
