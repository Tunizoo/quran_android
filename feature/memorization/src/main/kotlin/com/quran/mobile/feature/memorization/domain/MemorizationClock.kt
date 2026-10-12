package com.quran.mobile.feature.memorization.domain

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.util.TimeZone

/**
 * Time source for the memorization schedule. Days are local epoch days (days since 1970-01-01 in
 * the device's time zone) so that "today" rolls over at local midnight.
 */
interface MemorizationClock {
  fun nowMillis(): Long
  fun dayOf(millis: Long): Int
  fun today(): Int = dayOf(nowMillis())

  companion object {
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L
  }
}

@ContributesBinding(AppScope::class)
class SystemMemorizationClock @Inject constructor() : MemorizationClock {
  override fun nowMillis(): Long = System.currentTimeMillis()

  override fun dayOf(millis: Long): Int {
    val offset = TimeZone.getDefault().getOffset(millis)
    return Math.floorDiv(millis + offset, MemorizationClock.DAY_MILLIS).toInt()
  }
}
