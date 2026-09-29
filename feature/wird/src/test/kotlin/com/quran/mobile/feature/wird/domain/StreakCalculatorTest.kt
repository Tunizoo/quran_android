package com.quran.mobile.feature.wird.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreakCalculatorTest {
  private val today = 20_000

  @Test
  fun `no reading means no streak`() {
    val result = StreakCalculator.compute(emptyMap(), goal = 4, today = today)
    assertThat(result).isEqualTo(StreakCalculator.Result(current = 0, best = 0, isTodayMet = false))
  }

  @Test
  fun `consecutive days ending today count`() {
    val counts = mapOf(today - 2 to 4, today - 1 to 5, today to 4)
    val result = StreakCalculator.compute(counts, goal = 4, today = today)
    assertThat(result.current).isEqualTo(3)
    assertThat(result.best).isEqualTo(3)
    assertThat(result.isTodayMet).isTrue()
  }

  @Test
  fun `streak survives when today is not yet met`() {
    val counts = mapOf(today - 2 to 4, today - 1 to 4, today to 1)
    val result = StreakCalculator.compute(counts, goal = 4, today = today)
    assertThat(result.current).isEqualTo(2)
    assertThat(result.isTodayMet).isFalse()
  }

  @Test
  fun `a missed day breaks the current streak but keeps the best`() {
    val counts = mapOf(today - 5 to 4, today - 4 to 4, today - 3 to 4, today - 1 to 4, today to 4)
    val result = StreakCalculator.compute(counts, goal = 4, today = today)
    assertThat(result.current).isEqualTo(2)
    assertThat(result.best).isEqualTo(3)
  }

  @Test
  fun `days below the goal do not count`() {
    val counts = mapOf(today - 1 to 3, today to 4)
    val result = StreakCalculator.compute(counts, goal = 4, today = today)
    assertThat(result.current).isEqualTo(1)
    assertThat(result.best).isEqualTo(1)
  }

  @Test
  fun `streak that ended before yesterday is not current`() {
    val counts = mapOf(today - 4 to 4, today - 3 to 4)
    val result = StreakCalculator.compute(counts, goal = 4, today = today)
    assertThat(result.current).isEqualTo(0)
    assertThat(result.best).isEqualTo(2)
  }

  @Test
  fun `goal of zero is treated as one page`() {
    val counts = mapOf(today to 1)
    val result = StreakCalculator.compute(counts, goal = 0, today = today)
    assertThat(result.current).isEqualTo(1)
  }
}
