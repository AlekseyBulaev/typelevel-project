package com.typelevel.jobsboard.playground

import cats.effect.{ExitCode, IO, IOApp}
import com.typelevel.jobsboard.core.*
import com.typelevel.jobsboard.config.*

import java.util.Properties
import javax.mail.Message.RecipientType
import javax.mail.internet.MimeMessage
import javax.mail.{Authenticator, PasswordAuthentication, Session, Transport}

object EmailsPlayground {
  def main(in: Array[String]): Unit = {

    val host        = "smtp.ethereal.email"
    val port        = 587
    val user        = "lilla.christiansen@ethereal.email"
    val pass        = "XVW8pjRKFBG6uHNqth"
    val frontendUrl = "https://google.com"
    val token       = "ABCD1234"

    val prop = new Properties()
    prop.put("mail.smtp.auth", true)
    prop.put("mail.smtp.starttls.enable", true)
    prop.put("mail.smtp.host", host)
    prop.put("mail.smtp.port", port)
    prop.put("mail.smtp.ssl.trust", host)

    val auth = new Authenticator {
      override def getPasswordAuthentication: PasswordAuthentication =
        new PasswordAuthentication(user, pass)
    }

    val session = Session.getInstance(prop, auth)

    val subject = "Email from jobsBoard"
    val content =
      s"""
    <div style="
      border: 1px solid black;
      padding: 20px;
      font-family: sans-serif;
      line-height: 2;
      font-size: 20px;
    ">
    <h1>JobsBoard password recovery</h1>
    <p> Your password recovery token is: $token</p>
    <p>
      Click <a href="$frontendUrl/login">here</a> to get back to the application
    </p>
    <p> From jobsBoard </p>
    </div>
    """

    val message = new MimeMessage(session)

    message.setFrom("admin@jobsBoard.com")
    message.setRecipients(RecipientType.TO, "the.user@gmail.com")
    message.setSubject(subject)
    message.setContent(content, "text/html; charset=utf-8")

    Transport.send(message)
  }
}

object EmailsEffectPlayground extends IOApp.Simple {
   override def run: IO[Unit] = for {
    emails <- LiveEmails[IO](
      EmailServiceConfig(
        host = "smtp.ethereal.email",
        port = 587,
        user = "lilla.christiansen@ethereal.email",
        pass = "XVW8pjRKFBG6uHNqth",
        frontendUrl = "https://google.com"
      )
    )
    _ <- emails.sendPasswordRecoveryEmail("someone@mail.com", "TOKEN")
  } yield ()
}
