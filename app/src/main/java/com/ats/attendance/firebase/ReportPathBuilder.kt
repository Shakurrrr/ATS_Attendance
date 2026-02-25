package com.ats.attendance.firebase

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

object ReportPathBuilder {
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd

    fun dailyPath(date: LocalDate): String {
        val yyyyMMdd = date.format(dateFmt)
        return "reports/daily/$yyyyMMdd.pdf"
    }

    fun weeklyPath(date: LocalDate): String {
        val wf = WeekFields.ISO
        val week = date.get(wf.weekOfWeekBasedYear())
        val weekYear = date.get(wf.weekBasedYear())
        val yyyyWww = "%04d-W%02d".format(weekYear, week)
        return "reports/weekly/$yyyyWww.pdf"
    }

    fun storagePath(mode: ReportMode, date: LocalDate): String =
        when (mode) {
            ReportMode.DAILY -> dailyPath(date)
            ReportMode.WEEKLY -> weeklyPath(date)
        }

    fun displayLabel(mode: ReportMode, date: LocalDate): String =
        when (mode) {
            ReportMode.DAILY -> date.format(dateFmt)
            ReportMode.WEEKLY -> {
                val wf = WeekFields.ISO
                val week = date.get(wf.weekOfWeekBasedYear())
                val weekYear = date.get(wf.weekBasedYear())
                "%04d-W%02d".format(weekYear, week)
            }
        }
}