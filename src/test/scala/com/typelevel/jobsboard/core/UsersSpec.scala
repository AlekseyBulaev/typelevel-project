package com.typelevel.jobsboard.core

import cats.effect.*
import doobie.*
import doobie.util.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import cats.effect.testing.scalatest.AsyncIOSpec
import com.typelevel.jobsboard.domain.user.User
import com.typelevel.jobsboard.fixtures.UserFixture
import org.postgresql.util.PSQLException
import org.scalatest.Inside
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
class UsersSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Inside
    with DoobieSpec
    with UserFixture {
  override val initScript: String = "sql/users.sql"
  given Logger: Logger[IO]        = Slf4jLogger.getLogger[IO]

  "Users 'algebra'" - {
    "should retrieve a user by email" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          retrieved <- users.find(Bill.email)
        } yield retrieved
        program.asserting(_ shouldBe Some(Bill))
      }
    }

    "should return None if the email doesn't exists" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          retrieved <- users.find("notFound@mail.com")
        } yield retrieved
        program.asserting(_ shouldBe None)
      }
    }

    "should create a new user" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          userId <- users.create(NewUser)
          maybeUser <- sql"SELECT * FROM users WHERE email = ${NewUser.email}"
            .query[User]
            .option
            .transact(xa)

        } yield (userId, maybeUser)
        program.asserting { case (userId, maybeUser) =>
          userId shouldBe NewUser.email
          maybeUser shouldBe Some(NewUser)
        }
      }
    }

    "should fail creating a new user if a user already exists" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          userId <- users.create(Bill).attempt
        } yield userId
        program.asserting { outcome =>
          inside(outcome) {
            case Left(e) =>
              e shouldBe a[PSQLException]
            case _ => fail()
          }
        }
      }
    }

    "should return None when updating a user that doesn't exists" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          maybeUser <- users.update(NewUser)
        } yield maybeUser
        program.asserting(_ shouldBe None)
      }
    }

    "should update an existing user" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          maybeUser <- users.update(UpdatedBill)
        } yield maybeUser
        program.asserting(_ shouldBe Some(UpdatedBill))
      }
    }

    "should delete an existing user" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          result <- users.delete(John.email)
          maybeUser <- sql"SELECT * FROM users WHERE email = ${John.email}"
            .query[User]
            .option
            .transact(xa)
        } yield (result, maybeUser)
        program.asserting { case (result, maybeUser) =>
          result shouldBe true
          maybeUser shouldBe None
        }
      }

    }

    "should NOT delete a user that doesn't exists" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          result <- users.delete("notfound@mail.com")
        } yield result
        program.asserting(_ shouldBe false)
      }
    }

  }
}
