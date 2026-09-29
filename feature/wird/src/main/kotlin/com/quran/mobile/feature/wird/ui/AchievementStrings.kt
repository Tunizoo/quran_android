package com.quran.mobile.feature.wird.ui

import androidx.annotation.StringRes
import com.quran.mobile.feature.wird.R
import com.quran.mobile.feature.wird.model.Achievement

/** Display metadata for each [Achievement]. Kept outside Compose so notifications can use it too. */
object AchievementStrings {
  @StringRes
  fun title(achievement: Achievement): Int = when (achievement) {
    Achievement.FIRST_PAGE -> R.string.wird_ach_first_page_title
    Achievement.FIRST_GOAL -> R.string.wird_ach_first_goal_title
    Achievement.STREAK_3 -> R.string.wird_ach_streak_3_title
    Achievement.STREAK_7 -> R.string.wird_ach_streak_7_title
    Achievement.STREAK_30 -> R.string.wird_ach_streak_30_title
    Achievement.STREAK_100 -> R.string.wird_ach_streak_100_title
    Achievement.PAGES_100 -> R.string.wird_ach_pages_100_title
    Achievement.PAGES_300 -> R.string.wird_ach_pages_300_title
    Achievement.PAGES_1000 -> R.string.wird_ach_pages_1000_title
    Achievement.JUZ_IN_A_DAY -> R.string.wird_ach_juz_in_a_day_title
    Achievement.FAJR_READER -> R.string.wird_ach_fajr_reader_title
    Achievement.KHATMA_1 -> R.string.wird_ach_khatma_1_title
    Achievement.KHATMA_3 -> R.string.wird_ach_khatma_3_title
  }

  @StringRes
  fun description(achievement: Achievement): Int = when (achievement) {
    Achievement.FIRST_PAGE -> R.string.wird_ach_first_page_desc
    Achievement.FIRST_GOAL -> R.string.wird_ach_first_goal_desc
    Achievement.STREAK_3 -> R.string.wird_ach_streak_3_desc
    Achievement.STREAK_7 -> R.string.wird_ach_streak_7_desc
    Achievement.STREAK_30 -> R.string.wird_ach_streak_30_desc
    Achievement.STREAK_100 -> R.string.wird_ach_streak_100_desc
    Achievement.PAGES_100 -> R.string.wird_ach_pages_100_desc
    Achievement.PAGES_300 -> R.string.wird_ach_pages_300_desc
    Achievement.PAGES_1000 -> R.string.wird_ach_pages_1000_desc
    Achievement.JUZ_IN_A_DAY -> R.string.wird_ach_juz_in_a_day_desc
    Achievement.FAJR_READER -> R.string.wird_ach_fajr_reader_desc
    Achievement.KHATMA_1 -> R.string.wird_ach_khatma_1_desc
    Achievement.KHATMA_3 -> R.string.wird_ach_khatma_3_desc
  }

  fun emoji(achievement: Achievement): String = when (achievement) {
    Achievement.FIRST_PAGE -> "📖"
    Achievement.FIRST_GOAL -> "🎯"
    Achievement.STREAK_3 -> "🔥"
    Achievement.STREAK_7 -> "🌙"
    Achievement.STREAK_30 -> "🌟"
    Achievement.STREAK_100 -> "💎"
    Achievement.PAGES_100 -> "📚"
    Achievement.PAGES_300 -> "🏛️"
    Achievement.PAGES_1000 -> "🕌"
    Achievement.JUZ_IN_A_DAY -> "⚡"
    Achievement.FAJR_READER -> "🌅"
    Achievement.KHATMA_1 -> "🏆"
    Achievement.KHATMA_3 -> "👑"
  }
}
