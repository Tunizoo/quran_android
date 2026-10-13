package com.quran.mobile.feature.wird.domain

enum class ChallengeType {
  /** Read a number of pages during the week. */
  PAGES,

  /** Reach the daily goal on a number of days of the week. */
  GOAL_DAYS,

  /** Grade a number of memorization reviews during the week. */
  REVIEWS
}

data class Challenge(val week: Int, val type: ChallengeType, val target: Int)

data class ChallengeProgress(
  val challenge: Challenge,
  val progress: Int,
  val completed: Boolean,
  /** Days left in the week including today. */
  val daysLeft: Int
) {
  val fraction: Float
    get() = if (challenge.target <= 0) 0f else (progress.toFloat() / challenge.target).coerceIn(0f, 1f)
}

/**
 * One challenge per calendar week, chosen deterministically from the week number so every device
 * agrees on it without a server. Weeks start on Monday; days are local epoch days.
 */
object WeeklyChallenge {
  /** 1970-01-01 was a Thursday; shifting by 3 makes week boundaries fall on Mondays. */
  fun weekOf(day: Int): Int = Math.floorDiv(day + 3, 7)

  fun firstDayOfWeek(week: Int): Int = week * 7 - 3

  fun daysLeftInWeek(day: Int): Int = firstDayOfWeek(weekOf(day) + 1) - day

  /** The challenge of [week] for a user whose daily goal is [goalPages]. */
  fun forWeek(week: Int, goalPages: Int): Challenge {
    val type = ChallengeType.entries[Math.floorMod(week, ChallengeType.entries.size)]
    val target = when (type) {
      ChallengeType.PAGES -> (goalPages * 5).coerceIn(10, 150)
      ChallengeType.GOAL_DAYS -> 5
      ChallengeType.REVIEWS -> 5
    }
    return Challenge(week, type, target)
  }
}
