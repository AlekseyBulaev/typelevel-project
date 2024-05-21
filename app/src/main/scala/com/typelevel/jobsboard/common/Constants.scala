package com.typelevel.jobsboard.common

import scala.util.matching.Regex

object Constants {
    val emailRegex: String = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""

    object Endoints {
        val root = "http://localhost:8080"
        val signup = s"$root/api/auth/users"
        val login = s"$root/api/auth/login"
    }
}