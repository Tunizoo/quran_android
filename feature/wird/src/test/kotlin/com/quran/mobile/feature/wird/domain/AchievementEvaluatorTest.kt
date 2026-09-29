package com.quran.mobile.feature.wird.domain

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.wird.model.Achievement
import org.junit.Test

class AchievementEvaluatorTest {
  private val nothing = WirdStats(
    totalPagesRead = 0,
    daysGoalMet = 0,
    bestStreak = 0,
    bestDayPages = 0,
    earlyMorningViews = 0,
    completedKhatmas = 0
  )

  @Test
  fun `no stats unlock nothing`() {
    assertThat(AchievementEvaluator.evaluate(nothing)).isEmpty()
  }

  @Test
  fun `first page unlocks only the first page achievement`() {
    val earned = AchievementEvaluator.evaluate(nothing.copy(totalPagesRead = 1, bestDayPages = 1))
    assertThat(earned).containsExactly(Achievement.FIRST_PAGE)
  }

  @Test
  fun `streak thresholds are cumulative`() {
    val earned = AchievementEvaluator.evaluate(
      nothing.copy(totalPagesRead = 30, daysGoalMet = 7, bestStreak = 7, bestDayPages = 5)
    )
    assertThat(earned).containsAtLeast(
      Achievement.FIRST_PAGE,
      Achievement.FIRST_GOAL,
      Achievement.STREAK_3,
      Achievement.STREAK_7
    )
    assertThat(earned).doesNotContain(Achievement.STREAK_30)
  }

  @Test
  fun `juz in a day requires twenty pages on one day`() {
    assertThat(AchievementEvaluator.evaluate(nothing.copy(totalPagesRead = 19, bestDayPages = 19)))
      .doesNotContain(Achievement.JUZ_IN_A_DAY)
    assertThat(AchievementEvaluator.evaluate(nothing.copy(totalPagesRead = 20, bestDayPages = 20)))
      .contains(Achievement.JUZ_IN_A_DAY)
  }

  @Test
  fun `khatma achievements follow completed khatmas`() {
    assertThat(AchievementEvaluator.evaluate(nothing.copy(totalPagesRead = 604, completedKhatmas = 1)))
      .contains(Achievement.KHATMA_1)
    assertThat(AchievementEvaluator.evaluate(nothing.copy(totalPagesRead = 604, completedKhatmas = 1)))
      .doesNotContain(Achievement.KHATMA_3)
    assertThat(AchievementEvaluator.evaluate(nothing.copy(totalPagesRead = 1812, completedKhatmas = 3)))
      .contains(Achievement.KHATMA_3)
  }

  @Test
  fun `fajr reader needs at least one early view`() {
    assertThat(AchievementEvaluator.evaluate(nothing.copy(totalPagesRead = 1, earlyMorningViews = 1)))
      .contains(Achievement.FAJR_READER)
  }
}
