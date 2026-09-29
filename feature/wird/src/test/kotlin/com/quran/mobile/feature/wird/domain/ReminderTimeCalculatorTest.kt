package com.quran.mobile.feature.wird.domain

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.wird.model.ReminderMode
import com.quran.mobile.feature.wird.model.WirdSettings
import org.junit.Test

class ReminderTimeCalculatorTest {
  private val fixed = WirdSettings.DEFAULT.copy(reminderEnabled = true, reminderMinuteOfDay = 20 * 60)
  private val adaptive = fixed.copy(reminderMode = ReminderMode.ADAPTIVE)

  @Test
  fun `fixed mode ignores history`() {
    val minute = ReminderTimeCalculator.targetMinuteOfDay(fixed, listOf(6 * 60, 6 * 60 + 30, 7 * 60))
    assertThat(minute).isEqualTo(20 * 60)
  }

  @Test
  fun `adaptive mode falls back to fixed time without enough history`() {
    val minute = ReminderTimeCalculator.targetMinuteOfDay(adaptive, listOf(6 * 60, 6 * 60 + 30))
    assertThat(minute).isEqualTo(20 * 60)
  }

  @Test
  fun `adaptive mode uses the median first reading minute plus the grace period`() {
    val minute = ReminderTimeCalculator.targetMinuteOfDay(adaptive, listOf(21 * 60, 13 * 60, 13 * 60 + 30))
    assertThat(minute).isEqualTo(13 * 60 + 30 + ReminderTimeCalculator.ADAPTIVE_GRACE_MINUTES)
  }

  @Test
  fun `adaptive mode clamps to the allowed window`() {
    val early = ReminderTimeCalculator.targetMinuteOfDay(adaptive, listOf(4 * 60, 4 * 60, 5 * 60))
    assertThat(early).isEqualTo(ReminderTimeCalculator.ADAPTIVE_EARLIEST_MINUTE)

    val late = ReminderTimeCalculator.targetMinuteOfDay(adaptive, listOf(23 * 60, 23 * 60, 23 * 60))
    assertThat(late).isEqualTo(ReminderTimeCalculator.ADAPTIVE_LATEST_MINUTE)
  }

  @Test
  fun `delay until next occurrence is always within one day`() {
    val now = System.currentTimeMillis()
    for (minute in listOf(0, 6 * 60, 12 * 60, 20 * 60, 23 * 60 + 59)) {
      val delay = ReminderTimeCalculator.delayUntilNext(now, minute)
      assertThat(delay).isGreaterThan(0L)
      assertThat(delay).isAtMost(WirdClock.DAY_MILLIS + 60 * 60 * 1000L)
    }
  }
}
