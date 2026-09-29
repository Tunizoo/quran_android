package com.quran.mobile.feature.wird.model

/** How the daily reminder time is chosen. */
enum class ReminderMode(val storedValue: Long) {
  /** Fire at the configured minute of the day. */
  FIXED(0),

  /**
   * Learn the user's usual reading time from recent history and remind them shortly after it,
   * falling back to the fixed time when there is not enough history.
   */
  ADAPTIVE(1);

  companion object {
    fun fromStoredValue(value: Long): ReminderMode = entries.firstOrNull { it.storedValue == value } ?: FIXED
  }
}

data class WirdSettings(
  val pagesPerDay: Int,
  val reminderEnabled: Boolean,
  /** Minute of the (local) day at which the fixed reminder fires, e.g. 20 * 60 for 8pm. */
  val reminderMinuteOfDay: Int,
  val reminderMode: ReminderMode
) {
  companion object {
    const val MIN_PAGES_PER_DAY = 1
    const val MAX_PAGES_PER_DAY = 100

    val DEFAULT = WirdSettings(
      pagesPerDay = 4,
      reminderEnabled = false,
      reminderMinuteOfDay = 20 * 60,
      reminderMode = ReminderMode.FIXED
    )
  }
}

/** Number of pages read on a given local epoch day and the first minute of that day a page was read. */
data class DailyCount(val day: Int, val pages: Int, val firstMinuteOfDay: Int)

data class UnlockedAchievement(val achievement: Achievement, val unlockedAtMillis: Long)

data class CompletedKhatma(val number: Int, val completedAtMillis: Long)

data class WirdSummary(
  val today: Int,
  val todayPages: Int,
  val goal: Int,
  val currentStreak: Int,
  val bestStreak: Int,
  val isTodayGoalMet: Boolean,
  val totalPagesRead: Int,
  val khatmaNumber: Int,
  val khatmaPagesRead: Int,
  val mushafPageCount: Int,
  val completedKhatmas: List<CompletedKhatma>,
  val unlockedAchievements: List<UnlockedAchievement>,
  /** Daily counts for the most recent days, most recent first. */
  val recentDays: List<DailyCount>
) {
  val remainingToday: Int get() = (goal - todayPages).coerceAtLeast(0)
  val khatmaProgress: Float
    get() = if (mushafPageCount <= 0) 0f else (khatmaPagesRead.toFloat() / mushafPageCount).coerceIn(0f, 1f)
}

/** Outcome of recording a page view. */
data class RecordResult(
  val wasNewPageToday: Boolean,
  val newAchievements: List<Achievement>,
  val completedKhatmaNumber: Int?
) {
  companion object {
    val NONE = RecordResult(wasNewPageToday = false, newAchievements = emptyList(), completedKhatmaNumber = null)
  }
}
