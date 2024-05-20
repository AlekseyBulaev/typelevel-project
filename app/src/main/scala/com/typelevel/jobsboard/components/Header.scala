package com.typelevel.jobsboard.components

import tyrian.*
import tyrian.Html.*
import scala.scalajs.js
import scala.scalajs.js.annotation.*

import com.typelevel.jobsboard.core.*
import com.typelevel.jobsboard.pages.Page.Urls
object Header {
  // public API
  def view() =
    div(`class` := "header-container")(
      renderLogo(),
      div(`class` := "header-nav")(
        ul(`class` := "header-links")(
          renderNavLink("Jobs", Urls.JOBS),
          renderNavLink("Login", Urls.LOGIN),
          renderNavLink("Sign up", Urls.SIGNUP)
        )
      )
    )

  // private API
  @js.native
  @JSImport("/static/img/fiery-lava.png", JSImport.Default)
  private val logoImage: String = js.native
  private def renderLogo() =
    a(
      href := "/",
      onEvent(
        "click",
        e => {
          e.preventDefault() // native JS - prevent reloading the page
          Router.ChangeLocation("/")
        }
      )
    )(
      img(
        `class` := "home-logo",
        src := logoImage,
        alt := "JobsBoardLogo"
      )
    )
  private def renderNavLink(text: String, location: String) =
    // header <a href="/jobs">Jobs</a>
    li(`class` := "nav-item")(
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
    )
}
