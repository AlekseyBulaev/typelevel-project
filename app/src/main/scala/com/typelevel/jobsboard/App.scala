package com.typelevel.jobsboard

import tyrian.*
import tyrian.Html.*
import cats.effect.IO

import scala.scalajs.js.annotation.*
import org.scalajs.dom.{console, document}
import tyrian.cmds.Logger

import scala.concurrent.duration.*

object App {
  sealed trait Msg
  case class Increment(amount: Int) extends Msg
  case class Model(count: Int) extends Msg
}
@JSExportTopLevel("JobsBoardApp")
class App extends TyrianApp[App.Msg, App.Model] {
  import App.*
  /*
    We can send messages by
    - trigger a command
    - create a subscription
    - listening for an event
   */
  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) =
    (Model(0), Cmd.None)

  // potentially the endless stream of messages
  override def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.every[IO](1.second).map(_ => Increment(1))

  // model can change by receiving messages
  // model => message => (new model, new command)
  // update triggered whenever we get a new message
  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {case Increment(amount) =>
    (model.copy(count = model.count + amount), Logger.consoleLog[IO]("Changing count by " + amount))
  }

  // view triggered whenever the model changes
  override def view(model: Model): Html[Msg] =
    div(
      button(onClick(Increment(1)))("increment"),
      button(onClick(Increment(-1)))("decrement"),
      div(s"Tyrian running: ${model.count}")
    )

}
