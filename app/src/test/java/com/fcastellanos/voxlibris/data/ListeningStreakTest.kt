package com.fcastellanos.voxlibris.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStreakTest {

    // Jueves 19 de noviembre de 2026.
    private val today: LocalDate = LocalDate.of(2026, 11, 19)

    @Test
    fun `counts consecutive days ending today`() {
        val days = mapOf(
            today to 600L,
            today.minusDays(1) to 1200L,
            today.minusDays(2) to 60L,
            today.minusDays(3) to 30L,
            today.minusDays(5) to 900L,
        )
        assertEquals(4, ListeningStreak.summarize(days, today).streakDays)
    }

    @Test
    fun `a streak survives until the current day ends`() {
        val days = mapOf(today.minusDays(1) to 100L, today.minusDays(2) to 100L)
        assertEquals(2, ListeningStreak.summarize(days, today).streakDays)
        assertEquals(0, ListeningStreak.summarize(mapOf(today.minusDays(2) to 100L), today).streakDays)
    }

    @Test
    fun `week sums monday to today and marks the dots`() {
        val monday = LocalDate.of(2026, 11, 16)
        val days = mapOf(
            monday to 3600L,
            monday.plusDays(1) to 1800L,
            today to 600L,
            monday.minusDays(1) to 9999L, // domingo anterior: no cuenta
            monday.plusDays(5) to 500L, // sábado futuro: no cuenta
        )
        val summary = ListeningStreak.summarize(days, today)
        assertEquals(6000L, summary.weekSeconds)
        assertEquals(7, summary.week.size)
        assertEquals(listOf(true, true, false, true, false, false, false), summary.week.map { it.listened })
        assertTrue(summary.week[3].isToday)
        assertFalse(summary.isEmpty)
    }

    @Test
    fun `no listening is empty`() {
        assertTrue(ListeningStreak.summarize(emptyMap(), today).isEmpty)
    }
}
