package com.quran.mobile.feature.wird.domain

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.util.Calendar
import java.util.TimeZone

/**
 * Time source for the wird feature. Days are represented as local epoch days (days since
 * 1970-01-01 in the device's time zone) so that "today" rolls over at local midnight.
 */
interface WirdClock {
  fun nowMillis(): Long
  fun dayOf(millis: Long): Int
  fun minuteOfDayOf(millis: Long): Int
  fun today(): Int = dayOf(nowMillis())

  companion object {
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L
  }
}

@ContributesBinding(AppScope::class)
class SystemWirdClock @Inject constructor() : WirdClock {
  override fun nowMillis(): Long = System.currentTimeMillis()

  override fun dayOf(millis: Long): Int {
    val offset = TimeZone.getDefault().getOffset(millis)
    return Math.floorDiv(millis + offset, WirdClock.DAY_MILLIS).toInt()
  }

  override fun minuteOfDayOf(millis: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
  }
}
