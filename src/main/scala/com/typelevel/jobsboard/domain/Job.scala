package com.typelevel.jobsboard.domain

import java.util.UUID

object Job {
  case class Job(
                  id: UUID, 
                  date: Long, 
                  ownerEmail: String, 
                  jobInfo: JobInfo, 
                  active: Boolean = false
                )

  case class JobInfo(
                      company: String,
                      title: String,
                      description: String,
                      externalUrl: String,
                      remote: Boolean = false,
                      location: String,
                      salaryLow: Option[Int],
                      salaryHigh: Option[Int],
                      currency: Option[String],
                      country: Option[String],
                      tags: Option[List[String]],
                      image: Option[String],
                      seniority: Option[String],
                      other: Option[String]
                    )

  object JobInfo {
    val empty: JobInfo = JobInfo("", "", "", "", false, "", None, None, None, None, None, None, None, None)
  }
}