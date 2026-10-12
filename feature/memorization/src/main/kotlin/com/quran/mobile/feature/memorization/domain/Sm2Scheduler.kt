package com.quran.mobile.feature.memorization.domain

import kotlin.math.max
import kotlin.math.roundToInt

/** The schedule of a portion: easiness factor, current interval and successful repetitions. */
data class Schedule(val ease: Double, val intervalDays: Int, val repetitions: Int) {
  companion object {
    val INITIAL = Schedule(ease = 2.5, intervalDays = 0, repetitions = 0)
  }
}

/**
 * Simplified SM-2 spaced repetition (SuperMemo). [quality] is 0..5: below 3 the portion is
 * forgotten and restarts tomorrow; otherwise the interval grows 1 → 6 → previous × ease, and the
 * ease moves with the quality (bounded below by 1.3). The result's interval is the number of days
 * until the next review.
 */
object Sm2Scheduler {
  const val MIN_EASE = 1.3
  const val MAX_INTERVAL_DAYS = 365

  fun next(current: Schedule, quality: Int): Schedule {
    val q = quality.coerceIn(0, 5)
    val ease = max(MIN_EASE, current.ease + 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
    if (q < 3) {
      return Schedule(ease = ease, intervalDays = 1, repetitions = 0)
    }
    val repetitions = current.repetitions + 1
    val interval = when (repetitions) {
      1 -> 1
      2 -> 6
      else -> (current.intervalDays * ease).roundToInt().coerceAtLeast(current.intervalDays + 1)
    }
    return Schedule(ease = ease, intervalDays = interval.coerceAtMost(MAX_INTERVAL_DAYS), repetitions = repetitions)
  }
}
