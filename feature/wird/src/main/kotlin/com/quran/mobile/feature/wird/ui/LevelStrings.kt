package com.quran.mobile.feature.wird.ui

import androidx.annotation.StringRes
import com.quran.mobile.feature.wird.R
import com.quran.mobile.feature.wird.domain.ChallengeType

/** Display metadata for the gamification layer (levels and weekly challenges). */
object LevelStrings {
  @StringRes
  fun title(level: Int): Int = when (level) {
    1 -> R.string.wird_level_1
    2 -> R.string.wird_level_2
    3 -> R.string.wird_level_3
    4 -> R.string.wird_level_4
    5 -> R.string.wird_level_5
    6 -> R.string.wird_level_6
    7 -> R.string.wird_level_7
    8 -> R.string.wird_level_8
    9 -> R.string.wird_level_9
    else -> R.string.wird_level_10
  }

  fun emoji(level: Int): String = when (level) {
    1 -> "🌱"
    2 -> "🌿"
    3 -> "🌾"
    4 -> "📗"
    5 -> "📘"
    6 -> "📙"
    7 -> "🥈"
    8 -> "🥇"
    9 -> "🏅"
    else -> "👑"
  }

  /** Description of the challenge with its target, e.g. "Read %d pages this week". */
  @StringRes
  fun challenge(type: ChallengeType): Int = when (type) {
    ChallengeType.PAGES -> R.string.wird_challenge_pages
    ChallengeType.GOAL_DAYS -> R.string.wird_challenge_goal_days
    ChallengeType.REVIEWS -> R.string.wird_challenge_reviews
  }
}
