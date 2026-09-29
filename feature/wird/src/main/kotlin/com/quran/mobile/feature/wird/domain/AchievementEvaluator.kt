package com.quran.mobile.feature.wird.domain

import com.quran.mobile.feature.wird.model.Achievement

/** Snapshot of the statistics achievements are evaluated against. */
data class WirdStats(
  val totalPagesRead: Int,
  val daysGoalMet: Int,
  val bestStreak: Int,
  val bestDayPages: Int,
  val earlyMorningViews: Int,
  val completedKhatmas: Int
)

/**
 * Pure evaluation of which achievements are earned for a given [WirdStats]. Achievements are
 * monotonic: once the repository persists an unlock it is never revoked, even if a later
 * evaluation (e.g. after raising the goal) would no longer produce it.
 */
object AchievementEvaluator {
  /** Pages in one juz of the madani mushaf, used for the "juz in a day" achievement. */
  const val JUZ_PAGES = 20

  /** Local minutes bounding the "fajr reader" window: 04:00 (inclusive) to 07:00 (exclusive). */
  const val FAJR_WINDOW_START_MINUTE = 4 * 60
  const val FAJR_WINDOW_END_MINUTE = 7 * 60

  fun evaluate(stats: WirdStats): Set<Achievement> {
    val earned = mutableSetOf<Achievement>()
    if (stats.totalPagesRead >= 1) earned += Achievement.FIRST_PAGE
    if (stats.daysGoalMet >= 1) earned += Achievement.FIRST_GOAL
    if (stats.bestStreak >= 3) earned += Achievement.STREAK_3
    if (stats.bestStreak >= 7) earned += Achievement.STREAK_7
    if (stats.bestStreak >= 30) earned += Achievement.STREAK_30
    if (stats.bestStreak >= 100) earned += Achievement.STREAK_100
    if (stats.totalPagesRead >= 100) earned += Achievement.PAGES_100
    if (stats.totalPagesRead >= 300) earned += Achievement.PAGES_300
    if (stats.totalPagesRead >= 1000) earned += Achievement.PAGES_1000
    if (stats.bestDayPages >= JUZ_PAGES) earned += Achievement.JUZ_IN_A_DAY
    if (stats.earlyMorningViews >= 1) earned += Achievement.FAJR_READER
    if (stats.completedKhatmas >= 1) earned += Achievement.KHATMA_1
    if (stats.completedKhatmas >= 3) earned += Achievement.KHATMA_3
    return earned
  }
}
