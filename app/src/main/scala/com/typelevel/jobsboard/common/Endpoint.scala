package com.typelevel.jobsboard.common

import tyrian.*
import tyrian.http.{Decoder, *}
import cats.effect.IO
import io.circe.*
import io.circe.syntax.*
trait Endpoint[M] {
  val location: String
  val method: Method
  val onSuccess: Response => M
  val onError: HttpError => M

  def call[A: Encoder](payload: A): Cmd[IO, M] =
    Http.send(
      // url
      // payload
      // headers
      Request(
        url = location,
        method = method,
        headers = List(),
        body = Body.json(payload.asJson.toString),
        timeout = Request.DefaultTimeOut,
        withCredentials = false
      ),
      Decoder[M](onSuccess, onError)
    )
}
