package com.quran.mobile.feature.wird.data

import com.quran.mobile.feature.wird.domain.WirdClock

/** A clock in a fixed UTC-like zone where day boundaries are multiples of [WirdClock.DAY_MILLIS]. */
class FakeWirdClock(var now: Long = 20_000L * WirdClock.DAY_MILLIS + 10 * 60 * 60 * 1000L) : WirdClock {
  override fun nowMillis(): Long = now
  override fun dayOf(millis: Long): Int = Math.floorDiv(millis, WirdClock.DAY_MILLIS).toInt()
  override fun minuteOfDayOf(millis: Long): Int = (Math.floorMod(millis, WirdClock.DAY_MILLIS) / 60_000L).toInt()

  fun advanceDays(days: Int) {
    now += days * WirdClock.DAY_MILLIS
  }

  fun setMinuteOfDay(minute: Int) {
    now = dayOf(now) * WirdClock.DAY_MILLIS + minute * 60_000L
  }
}
