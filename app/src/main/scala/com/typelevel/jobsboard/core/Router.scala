package com.typelevel.jobsboard.core

import tyrian.*
import cats.effect.*
import com.typelevel.jobsboard.App
import fs2.dom.History

import com.typelevel.jobsboard.*

case class Router private(location: String, history: History[IO, String]) {

  import Router.*

  def update(msg: Msg): (Router, Cmd[IO, Msg]) = msg match {
    case ChangeLocation(newLocation, browserTriggered) =>
      if (location == newLocation) (this, Cmd.None)
      else {
        val historyCmd =
          if (browserTriggered) Cmd.None //browser action, no need to push location on history
          else goto(newLocation) // manual action need to push location on history

        (this.copy(location = newLocation), historyCmd)
      }
    case _ => (this, Cmd.None) // TODO to check external redirects as well
  }

  private def goto[M](location: String): Cmd[IO, M] = Cmd.SideEffect[IO] {
    history.pushState(location, location)
  }
}

object Router {
  trait Msg extends App.Msg

  case class ChangeLocation(location: String, browserTriggered: Boolean = false) extends Msg

  case class ExternalRedirect(location: String) extends Msg

  def startAt[M](initialLocation: String): (Router, Cmd[IO, M]) =
    val router = Router(initialLocation, History[IO, String])
    (router, router.goto(initialLocation))
}
