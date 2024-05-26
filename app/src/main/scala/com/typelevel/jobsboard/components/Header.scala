package com.typelevel.jobsboard.components

import tyrian.*
import tyrian.Html.*
import scala.scalajs.js
import scala.scalajs.js.annotation.*

import com.typelevel.jobsboard.*
import com.typelevel.jobsboard.core.*
import com.typelevel.jobsboard.pages.Page.Urls

object Header {
  // public API
  def view() =
    div(`class` := "header-container")(
      renderLogo(),
      div(`class` := "header-nav")(
        ul(`class` := "header-links")(
          renderNavLinks()
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

  private def renderNavLinks(): List[Html[App.Msg]] = {
    val constantLinks = List(
      renderSimpleNavLink("Jobs", Urls.JOBS)
    )
    val unauthenticatedLinks = List(
      renderSimpleNavLink("Login", Urls.LOGIN),
      renderSimpleNavLink("Sign up", Urls.SIGNUP)
    )
    val authenticatedLinks = List(
      renderNavLink("Log Out", Urls.HASH)( _ => Session.Logout)
    )

    constantLinks ++ (
      if (Session.isActive) authenticatedLinks else unauthenticatedLinks
      )
  }

  private def renderSimpleNavLink(text: String, location: String) =
    renderNavLink(text, location)(Router.ChangeLocation(_))
  private def renderNavLink(text: String, location: String)(location2Msg: String => App.Msg) =
  // header <a href="/jobs">Jobs</a>
    li(`class` := "nav-item")(
      a(
        href := location,
        `class` := "nav-link",
        onEvent(
          "click",
          e => {
            e.preventDefault() // native JS - prevent reloading the page
            location2Msg(location)
          }
        )
      )(text)
    )
}
