package com.quran.mobile.feature.wird.domain

import com.quran.mobile.feature.wird.model.ReminderMode
import com.quran.mobile.feature.wird.model.WirdSettings
import java.util.Calendar

/**
 * Decides at which local minute of the day the reminder should fire and how long until the next
 * occurrence. In [ReminderMode.ADAPTIVE] mode the reminder targets the user's usual reading time
 * (median of the first reading minute across recent days) plus a grace period, so the reminder
 * arrives when the user has apparently skipped their habitual session.
 */
object ReminderTimeCalculator {
  const val ADAPTIVE_GRACE_MINUTES = 60
  const val ADAPTIVE_MIN_HISTORY_DAYS = 3
  const val ADAPTIVE_HISTORY_DAYS = 14
  const val ADAPTIVE_EARLIEST_MINUTE = 7 * 60
  const val ADAPTIVE_LATEST_MINUTE = 22 * 60
  const val MINUTES_PER_DAY = 24 * 60

  fun targetMinuteOfDay(settings: WirdSettings, recentFirstMinutes: List<Int>): Int {
    if (settings.reminderMode == ReminderMode.FIXED || recentFirstMinutes.size < ADAPTIVE_MIN_HISTORY_DAYS) {
      return settings.reminderMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    }
    val sorted = recentFirstMinutes.sorted()
    val median = sorted[sorted.size / 2]
    return (median + ADAPTIVE_GRACE_MINUTES).coerceIn(ADAPTIVE_EARLIEST_MINUTE, ADAPTIVE_LATEST_MINUTE)
  }

  /** Milliseconds from [nowMillis] until the next time the local clock reads [minuteOfDay]. */
  fun delayUntilNext(nowMillis: Long, minuteOfDay: Int): Long {
    val calendar = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
      set(Calendar.MINUTE, minuteOfDay % 60)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    if (calendar.timeInMillis <= nowMillis) {
      calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return calendar.timeInMillis - nowMillis
  }
}
