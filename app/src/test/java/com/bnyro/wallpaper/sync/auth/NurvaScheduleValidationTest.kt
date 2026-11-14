package com.bnyro.wallpaper.sync.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NurvaScheduleValidationTest {
    @Test
    fun parses24HourTime() {
        assertEquals(9 to 5, parseNurvaScheduleTime("09:05"))
        assertEquals(23 to 59, parseNurvaScheduleTime("23:59"))
    }

    @Test
    fun rejectsMalformedOrOutOfRangeTime() {
        listOf("9:05", "24:00", "12:60", "noon", "12:30 PM").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { parseNurvaScheduleTime(value) }
        }
    }

    @Test
    fun validatesDaysAndZone() {
        validateNurvaSchedule(127, 9, 30, "America/Los_Angeles")
        assertThrows(IllegalArgumentException::class.java) {
            validateNurvaSchedule(0, 9, 30, "UTC")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateNurvaSchedule(1, 9, 30, "Not/A_Zone")
        }
    }

    @Test
    fun rendersSelectedWeekdays() {
        assertEquals("Mon Wed Fri", nurvaScheduleDaysLabel(1 or 4 or 16))
    }
}
