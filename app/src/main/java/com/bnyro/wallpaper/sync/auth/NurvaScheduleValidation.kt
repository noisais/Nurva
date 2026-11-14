package com.bnyro.wallpaper.sync.auth

import java.time.ZoneId

/** Parses the schedule editor's 24-hour clock value. */
internal fun parseNurvaScheduleTime(value: String): Pair<Int, Int> {
    val match = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""").matchEntire(value.trim())
        ?: throw IllegalArgumentException("Enter a valid 24-hour time in HH:mm format.")
    return match.groupValues[1].toInt() to match.groupValues[2].toInt()
}

/** Validates the exact fields accepted by the schedule RPC. */
internal fun validateNurvaSchedule(daysMask: Int, hour: Int, minute: Int, timeZoneId: String) {
    require(daysMask in 1..127) { "Choose at least one day of the week." }
    require(hour in 0..23) { "Schedule hour must be between 00 and 23." }
    require(minute in 0..59) { "Schedule minute must be between 00 and 59." }
    require(runCatching { ZoneId.of(timeZoneId) }.isSuccess) { "Choose a valid IANA time zone." }
}

internal fun nurvaScheduleDaysLabel(mask: Int): String {
    val days = listOf("Mon" to 1, "Tue" to 2, "Wed" to 4, "Thu" to 8, "Fri" to 16, "Sat" to 32, "Sun" to 64)
    return days.filter { ( _, bit) -> mask and bit != 0 }.joinToString(" ") { it.first }
        .ifBlank { "No days" }
}
