package com.quran.mobile.feature.wird.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GamificationTest {

  @Test
  fun `zero points is level one with nothing into it`() {
    val level = LevelCalculator.levelFor(0)
    assertThat(level.number).isEqualTo(1)
    assertThat(level.pointsIntoLevel).isEqualTo(0)
    assertThat(level.pointsForLevel).isEqualTo(100)
    assertThat(level.pointsToNextLevel).isEqualTo(100)
  }

  @Test
  fun `points inside a level are measured from its start`() {
    val level = LevelCalculator.levelFor(450)
    assertThat(level.number).isEqualTo(3)
    assertThat(level.pointsIntoLevel).isEqualTo(150)
    assertThat(level.pointsForLevel).isEqualTo(400)
    assertThat(level.fraction).isWithin(0.001f).of(0.375f)
  }

  @Test
  fun `the top level has no next level`() {
    val level = LevelCalculator.levelFor(1_000_000)
    assertThat(level.number).isEqualTo(LevelCalculator.MAX_LEVEL)
    assertThat(level.isMaxLevel).isTrue()
    assertThat(level.fraction).isEqualTo(1f)
  }

  @Test
  fun `weeks start on monday`() {
    // 1970-01-05 was a Monday (epoch day 4)
    assertThat(WeeklyChallenge.weekOf(4)).isEqualTo(1)
    assertThat(WeeklyChallenge.weekOf(3)).isEqualTo(0)
    assertThat(WeeklyChallenge.firstDayOfWeek(1)).isEqualTo(4)
    assertThat(WeeklyChallenge.daysLeftInWeek(4)).isEqualTo(7)
    assertThat(WeeklyChallenge.daysLeftInWeek(10)).isEqualTo(1)
  }

  @Test
  fun `challenge types rotate and targets follow the goal`() {
    assertThat(WeeklyChallenge.forWeek(0, 4).type).isEqualTo(ChallengeType.PAGES)
    assertThat(WeeklyChallenge.forWeek(1, 4).type).isEqualTo(ChallengeType.GOAL_DAYS)
    assertThat(WeeklyChallenge.forWeek(2, 4).type).isEqualTo(ChallengeType.REVIEWS)
    assertThat(WeeklyChallenge.forWeek(3, 4).type).isEqualTo(ChallengeType.PAGES)
    assertThat(WeeklyChallenge.forWeek(0, 4).target).isEqualTo(20)
    assertThat(WeeklyChallenge.forWeek(0, 1).target).isEqualTo(10)
    assertThat(WeeklyChallenge.forWeek(0, 100).target).isEqualTo(150)
  }
}
