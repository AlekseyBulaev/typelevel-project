package com.typelevel.jobsboard

import scala.scalajs.js.annotation.*
import org.scalajs.dom.document
@JSExportTopLevel("JobsBoardApp")
class App {
  @JSExport
  def doSomething(containerId: String): Unit = document.getElementById(containerId).innerHTML = "THE ELEMENT"
}
