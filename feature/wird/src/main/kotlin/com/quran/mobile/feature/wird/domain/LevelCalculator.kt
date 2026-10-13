package com.quran.mobile.feature.wird.domain

/** The user's level derived from their total points. [number] is 1-based. */
data class LevelProgress(
  val number: Int,
  val totalPoints: Int,
  /** Points earned since the start of this level. */
  val pointsIntoLevel: Int,
  /** Points needed to go from this level to the next; 0 at the top level. */
  val pointsForLevel: Int
) {
  val isMaxLevel: Boolean get() = pointsForLevel == 0
  val pointsToNextLevel: Int get() = (pointsForLevel - pointsIntoLevel).coerceAtLeast(0)
  val fraction: Float
    get() = if (isMaxLevel) 1f else (pointsIntoLevel.toFloat() / pointsForLevel).coerceIn(0f, 1f)
}

/** Levels are fixed point thresholds; each level needs roughly twice the previous one. */
object LevelCalculator {
  /** Total points at which each level (index + 1) starts. */
  val THRESHOLDS = intArrayOf(0, 100, 300, 700, 1_500, 3_000, 6_000, 12_000, 25_000, 50_000)

  val MAX_LEVEL: Int get() = THRESHOLDS.size

  fun levelFor(totalPoints: Int): LevelProgress {
    val points = totalPoints.coerceAtLeast(0)
    var index = 0
    while (index + 1 < THRESHOLDS.size && points >= THRESHOLDS[index + 1]) index++
    val start = THRESHOLDS[index]
    val next = THRESHOLDS.getOrNull(index + 1)
    return LevelProgress(
      number = index + 1,
      totalPoints = points,
      pointsIntoLevel = points - start,
      pointsForLevel = if (next == null) 0 else next - start
    )
  }
}
