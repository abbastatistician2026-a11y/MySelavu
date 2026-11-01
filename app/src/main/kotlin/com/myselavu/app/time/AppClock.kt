package com.myselavu.app.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The ONE place the app asks "what is today?". Everything (rollover, warnings, reminders)
 * goes through this, so tests and the debug "Time travel" tool can move the date.
 */
class AppClock(private val base: Clock = Clock.systemDefaultZone()) {

    /** Debug-only shift of "today" (Developer tools → Time travel). */
    @Volatile
    var offsetDays: Long = 0

    val clock: Clock
        get() = if (offsetDays == 0L) base else Clock.offset(base, Duration.ofDays(offsetDays))

    fun today(): LocalDate = LocalDate.now(clock)
    fun now(): LocalDateTime = LocalDateTime.now(clock)
    fun instant(): Instant = clock.instant()
    fun millis(): Long = clock.millis()
    val zone: ZoneId get() = base.zone

    companion object {
        fun fixed(date: LocalDate, zone: ZoneId = ZoneId.of("Asia/Kolkata")): AppClock =
            AppClock(Clock.fixed(date.atTime(10, 0).atZone(zone).toInstant(), zone))
    }
}
