package com.typelevel.jobsboard.pages

import cats.effect.*
import tyrian.*
import tyrian.Html.*

import com.typelevel.jobsboard.*

final case class NotFoundPage() extends Page {
  override def initCmd: Cmd[IO, App.Msg] = Cmd.None // TODO

  override def update(msg: App.Msg): (Page, Cmd[IO, App.Msg]) = (this, Cmd.None) // TODO

  override def view(): Html[App.Msg] = div("Ouch! This page doesn't exist")
}
