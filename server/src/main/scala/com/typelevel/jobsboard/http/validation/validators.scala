package com.typelevel.jobsboard.http.validation

import cats.*
import cats.implicits.*
import cats.data.*
import cats.data.Validated.*
import com.typelevel.jobsboard.domain.auth.*
import com.typelevel.jobsboard.domain.job.*
import com.typelevel.jobsboard.domain.user.*

import java.net.URL
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object validators {
  sealed trait ValidationFailure(val errorMessage: String)
  final case class EmptyField(fieldName: String) extends ValidationFailure(s"'$fieldName' is empty")
  final case class InvalidUrl(fieldName: String)
      extends ValidationFailure(s"'$fieldName' is not a valid URL")
  final case class InvalidEmail(fieldName: String)
      extends ValidationFailure(s"'$fieldName' is not a valid")

  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]
  trait Validator[A] {
    def validate(value: A): ValidationResult[A]
  }

  private val emailRegex: Regex =
    """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  def validateRequired[A](field: A, fieldName: String)(
      required: A => Boolean
  ): ValidationResult[A] =
    if (required(field)) field.validNel
    else EmptyField(fieldName).invalidNel

  def validateUrl[A](field: String, fieldName: String): ValidationResult[String] =
    Try(URL(field).toURI) match {
      case Success(_) => field.validNel
      case Failure(e) => InvalidUrl(fieldName).invalidNel
    }
  def validateEmail(field: String, fieldName: String): ValidationResult[String] =
    if (emailRegex.findFirstMatchIn(field).isDefined) field.validNel
    else InvalidEmail(fieldName).invalidNel
  given jobInfoValidator: Validator[JobInfo] = (jobInfo: JobInfo) => {
    val JobInfo(
      company,
      title,
      description,
      externalUrl,
      remote,
      location,
      salaryLow,
      salaryHigh,
      currency,
      country,
      tags,
      image,
      seniority,
      other
    ) = jobInfo

    val validCompany     = validateRequired(company, "company")(_.nonEmpty)
    val validTitle       = validateRequired(title, "title")(_.nonEmpty)
    val validDescription = validateRequired(description, "description")(_.nonEmpty)
    val validExternalUrl = validateUrl(externalUrl, "externalUrl")
    val validLocation    = validateRequired(location, "location")(_.nonEmpty)

    (
      validCompany,
      validTitle,
      validDescription,
      validExternalUrl,
      remote.validNel,
      validLocation,
      salaryLow.validNel,
      salaryHigh.validNel,
      currency.validNel,
      country.validNel,
      tags.validNel,
      image.validNel,
      seniority.validNel,
      other.validNel
    ).mapN(JobInfo.apply) // ValidatedNel[ValidationFailure, JobInfo]
  }

  given loginValidator: Validator[LoginInfo] = (loginInfo: LoginInfo) => {
    val validUserEmail = validateRequired(loginInfo.email, "email")(_.nonEmpty)
      .andThen(e => validateEmail(e, "email"))
    val validUserPassword = validateRequired(loginInfo.password, "password")(_.nonEmpty)
    (validUserEmail, validUserPassword).mapN(LoginInfo.apply)
  }
  given newUserInfoValidator: Validator[NewUserInfo] = (newUserInfo: NewUserInfo) => {
    val validUserEmail = validateRequired(newUserInfo.email, "email")(_.nonEmpty)
      .andThen(e => validateEmail(e, "email"))
    val validUserPassword = validateRequired(newUserInfo.password, "password")(_.nonEmpty)
    (
      validUserEmail,
      validUserPassword,
      newUserInfo.firstName.validNel,
      newUserInfo.lastName.validNel,
      newUserInfo.company.validNel
    ).mapN(NewUserInfo.apply)
  }

  given newPasswordInfoValidator: Validator[NewPasswordInfo] = (newPasswordInfo: NewPasswordInfo) =>
    {
      val validUserPassword =
        validateRequired(newPasswordInfo.oldPassword, "old password")(_.nonEmpty)
      val validNewPassword =
        validateRequired(newPasswordInfo.newPassword, "new password")(_.nonEmpty)
      (
        validUserPassword,
        validNewPassword
      ).mapN(NewPasswordInfo.apply)
    }
}
