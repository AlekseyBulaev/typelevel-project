package com.typelevel.jobsboard.pages

import cats.effect.*
import tyrian.*
import tyrian.Html.*

final case class ForgotPasswordPage() extends Page {
  override def initCmd: Cmd[IO, Page.Msg] = Cmd.None // TODO

  override def update(msg: Page.Msg): (Page, Cmd[IO, Page.Msg]) = (this, Cmd.None) // TODO

  override def view(): Html[Page.Msg] = div("Forgot password page - TODO")
}
