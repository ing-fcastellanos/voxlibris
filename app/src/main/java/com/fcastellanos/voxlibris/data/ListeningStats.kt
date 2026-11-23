package com.fcastellanos.voxlibris.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Racha y tiempo de escucha (tarjeta `vx-streak` de Inicio), calculados a partir de los segundos
 * escuchados por día. Lógica pura, sin Android, para poder probarla.
 */
object ListeningStreak {

    /** Un día de la semana en curso: si se escuchó, si es hoy. */
    data class Day(val date: LocalDate, val listened: Boolean, val isToday: Boolean)

    data class Summary(
        /** Días seguidos con escucha hasta hoy; si hoy todavía no se escuchó, la racha de ayer sigue viva. */
        val streakDays: Int,
        /** Segundos escuchados de lunes a hoy. */
        val weekSeconds: Long,
        /** Los siete días de la semana en curso, de lunes a domingo. */
        val week: List<Day>,
    ) {
        val isEmpty: Boolean get() = streakDays == 0 && weekSeconds == 0L
    }

    fun summarize(secondsByDay: Map<LocalDate, Long>, today: LocalDate): Summary {
        fun listened(day: LocalDate) = (secondsByDay[day] ?: 0L) > 0L
        var streak = 0
        var cursor = if (listened(today)) today else today.minusDays(1)
        while (listened(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        val monday = today.with(DayOfWeek.MONDAY)
        val week = (0L until 7L).map { offset ->
            val date = monday.plusDays(offset)
            Day(date, listened = date <= today && listened(date), isToday = date == today)
        }
        val weekSeconds = (0..ChronoUnit.DAYS.between(monday, today).toInt()).sumOf { secondsByDay[monday.plusDays(it.toLong())] ?: 0L }
        return Summary(streak, weekSeconds, week)
    }
}

/** Segundos escuchados por día, en preferencias; se conservan los últimos [KEEP_DAYS] días. */
class ListeningStatsStore(context: Context) {

    private val prefs = context.getSharedPreferences("listening_stats", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)
    /** Cambia con cada anotación, para recalcular la racha. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun add(day: LocalDate, seconds: Long) {
        if (seconds <= 0L) return
        val key = KEY_PREFIX + day
        prefs.edit().putLong(key, prefs.getLong(key, 0L) + seconds).apply()
        prune(day)
        _version.value++
    }

    fun secondsByDay(): Map<LocalDate, Long> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith(KEY_PREFIX) || value !is Long) return@mapNotNull null
        runCatching { LocalDate.parse(key.removePrefix(KEY_PREFIX)) }.getOrNull()?.let { it to value }
    }.toMap()

    fun summary(today: LocalDate = LocalDate.now()): ListeningStreak.Summary = ListeningStreak.summarize(secondsByDay(), today)

    private fun prune(today: LocalDate) {
        val limit = today.minusDays(KEEP_DAYS)
        val old = secondsByDay().keys.filter { it < limit }
        if (old.isEmpty()) return
        prefs.edit().apply { old.forEach { remove(KEY_PREFIX + it) } }.apply()
    }

    private companion object {
        const val KEY_PREFIX = "day:"
        const val KEEP_DAYS = 60L
    }
}
