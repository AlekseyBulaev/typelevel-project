package com.typelevel.jobsboard.fixtures

import cats.*
import cats.effect.*
import com.typelevel.jobsboard.core.Users
import com.typelevel.jobsboard.domain.user.*

trait UserFixture {
  val mockedUsers: Users[IO] = new Users[IO] {
    override def find(email: String): IO[Option[User]] =
      if (email == johnEmail) IO.pure(Some(John))
      else IO.pure(None)
    override def create(user: User): IO[String] = IO.pure(user.email)
    override def update(user: User): IO[Option[User]] = IO.pure(Some(user))
    override def delete(email: String): IO[Boolean] = IO.pure(true)
  }
  val John: User = User(
    "email@mail.com",
    "$2a$10$Sl9N0bw7x1XUab8tJdk5gubNgu4BuDQcXx1nMilWST8EH1h2P5lyu",
    Some("John"),
    Some("Doe"),
    Some("MAANG"),
    Role.ADMIN
  )
  val johnEmail: String = John.email
  val johnPassword = "email"
  val Bill: User = User(
    "boss@mail.com",
    "$2a$10$6sug9p3tF9gktbuuK8W.JOA//eBes1Vp3W/SXMtgQ/aNQUBDWuuHW",
    Some("Bill"),
    Some("Gates"),
    Some("Microsoft"),
    Role.RECRUITER
  )
  val billEmail: String = Bill.email
  val billPassword = "boss"
  val NewUser: User = User(
    "newuser@gmail.com",
    "$2a$10$6LQt4xy4LzqQihZiRZGG0eeeDwDCvyvthICXzPKQDQA3C47LtrQFy",
    Some("SOME"),
    Some("SOME"),
    Some("Some company"),
    Role.RECRUITER
  )
  val UpdatedBill: User = User(
    "boss@mail.com",
    "$2a$10$6sug9p3tF9gktbuuK8W.JOA//eBes1Vp3W/SXMtgQ/aNQUBDWuuHW",
    Some("Bill"),
    Some("GATES"),
    Some("Tesla"),
    Role.RECRUITER
  )
}
