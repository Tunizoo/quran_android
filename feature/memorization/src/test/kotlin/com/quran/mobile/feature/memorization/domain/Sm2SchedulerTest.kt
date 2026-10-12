package com.quran.mobile.feature.memorization.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Sm2SchedulerTest {

  @Test
  fun firstGoodReviewIsDueTomorrow() {
    val next = Sm2Scheduler.next(Schedule.INITIAL, quality = 4)
    assertThat(next.intervalDays).isEqualTo(1)
    assertThat(next.repetitions).isEqualTo(1)
    assertThat(next.ease).isWithin(0.001).of(2.5)
  }

  @Test
  fun intervalsGrowOneSixThenByEase() {
    var schedule = Schedule.INITIAL
    schedule = Sm2Scheduler.next(schedule, 4)
    schedule = Sm2Scheduler.next(schedule, 4)
    assertThat(schedule.intervalDays).isEqualTo(6)
    schedule = Sm2Scheduler.next(schedule, 4)
    assertThat(schedule.intervalDays).isEqualTo(15) // 6 * 2.5
    assertThat(schedule.repetitions).isEqualTo(3)
  }

  @Test
  fun forgettingRestartsTomorrowAndLowersEase() {
    val learned = Schedule(ease = 2.5, intervalDays = 15, repetitions = 3)
    val next = Sm2Scheduler.next(learned, quality = 1)
    assertThat(next.intervalDays).isEqualTo(1)
    assertThat(next.repetitions).isEqualTo(0)
    assertThat(next.ease).isLessThan(2.5)
  }

  @Test
  fun easeNeverDropsBelowMinimum() {
    var schedule = Schedule.INITIAL
    repeat(20) { schedule = Sm2Scheduler.next(schedule, 0) }
    assertThat(schedule.ease).isWithin(0.001).of(Sm2Scheduler.MIN_EASE)
  }

  @Test
  fun easyReviewsRaiseEaseAndIntervalIsCapped() {
    var schedule = Schedule.INITIAL
    repeat(12) { schedule = Sm2Scheduler.next(schedule, 5) }
    assertThat(schedule.ease).isGreaterThan(2.5)
    assertThat(schedule.intervalDays).isAtMost(Sm2Scheduler.MAX_INTERVAL_DAYS)
  }

  @Test
  fun hardReviewStillAdvancesButGrowsSlowly() {
    val learned = Schedule(ease = 2.5, intervalDays = 6, repetitions = 2)
    val next = Sm2Scheduler.next(learned, quality = 3)
    assertThat(next.repetitions).isEqualTo(3)
    assertThat(next.ease).isWithin(0.001).of(2.36)
    assertThat(next.intervalDays).isEqualTo(14) // 6 * 2.36 rounded
  }
}
