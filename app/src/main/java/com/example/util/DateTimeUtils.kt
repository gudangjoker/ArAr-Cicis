package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtils {
    val jakartaTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
    private val indonesianLocale = Locale("id", "ID")

    fun getCurrentJakartaTimeMillis(): Long {
        val cal = Calendar.getInstance(jakartaTimeZone)
        return cal.timeInMillis
    }

    /**
     * Start of day in Jakarta Time (00:00:00.000)
     */
    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(jakartaTimeZone)
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * End of day in Jakarta Time (23:59:59.999)
     */
    fun getEndOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(jakartaTimeZone)
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    /**
     * Start of current month in Jakarta Time
     */
    fun getStartOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(jakartaTimeZone)
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * End of current month in Jakarta Time
     */
    fun getEndOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(jakartaTimeZone)
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    /**
     * Formats timestamp into: "Kamis, 24 Sep 2026"
     */
    fun formatDateWithDay(timestamp: Long): String {
        val sdf = SimpleDateFormat("EEEE, d MMM yyyy", indonesianLocale)
        sdf.timeZone = jakartaTimeZone
        return sdf.format(Date(timestamp))
    }

    /**
     * Formats timestamp into: "24 Sep 2026, 14:30 WIB"
     */
    fun formatDateTimeJakarta(timestamp: Long): String {
        val sdf = SimpleDateFormat("d MMM yyyy, HH:mm 'WIB'", indonesianLocale)
        sdf.timeZone = jakartaTimeZone
        return sdf.format(Date(timestamp))
    }

    /**
     * Formats current month name: "September 2026"
     */
    fun formatCurrentMonth(timestamp: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("MMMM yyyy", indonesianLocale)
        sdf.timeZone = jakartaTimeZone
        return sdf.format(Date(timestamp))
    }

    /**
     * Formats short date: "24 Sep"
     */
    fun formatShortDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("d MMM", indonesianLocale)
        sdf.timeZone = jakartaTimeZone
        return sdf.format(Date(timestamp))
    }

    /**
     * Current month key for tracking monthly bill payments, e.g. "09-2026"
     */
    fun getCurrentMonthYearKey(timestamp: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("MM-yyyy", indonesianLocale)
        sdf.timeZone = jakartaTimeZone
        return sdf.format(Date(timestamp))
    }

    /**
     * Start of last month in Jakarta Time
     */
    fun getStartOfLastMonth(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(jakartaTimeZone)
        cal.timeInMillis = timestamp
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * End of last month in Jakarta Time
     */
    fun getEndOfLastMonth(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(jakartaTimeZone)
        cal.timeInMillis = timestamp
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}
