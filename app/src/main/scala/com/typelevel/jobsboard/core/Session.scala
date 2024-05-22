package com.typelevel.jobsboard.core

import tyrian.*
import cats.effect.IO
import tyrian.cmds.Logger

import com.typelevel.jobsboard.*

case class Session(email: Option[String] = None, token: Option[String] = None) {

  import Session.*

  def update(msg: Msg): (Session, Cmd[IO, Msg]) = msg match
    case SetToken(e, t) => (this.copy(Some(e), Some(t)), Logger.consoleLog[IO](s"Setting user session: $e - $t"))

  def initCmd: Cmd[IO, Msg] = Logger.consoleLog(s"Starting session monitoring")
}

object Session {
  trait Msg extends App.Msg

  case class SetToken(email: String, token: String) extends Msg
}
