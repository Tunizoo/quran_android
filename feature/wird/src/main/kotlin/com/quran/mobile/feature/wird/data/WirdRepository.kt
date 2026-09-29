package com.quran.mobile.feature.wird.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.quran.data.di.AppScope
import com.quran.mobile.feature.wird.db.DailyCounts
import com.quran.mobile.feature.wird.db.WirdDatabase
import com.quran.mobile.feature.wird.db.WirdQueries
import com.quran.mobile.feature.wird.db.Wird_achievement
import com.quran.mobile.feature.wird.db.Wird_khatma
import com.quran.mobile.feature.wird.db.Wird_settings
import com.quran.mobile.feature.wird.domain.AchievementEvaluator
import com.quran.mobile.feature.wird.domain.MushafPageCount
import com.quran.mobile.feature.wird.domain.StreakCalculator
import com.quran.mobile.feature.wird.domain.WirdClock
import com.quran.mobile.feature.wird.domain.WirdStats
import com.quran.mobile.feature.wird.model.Achievement
import com.quran.mobile.feature.wird.model.CompletedKhatma
import com.quran.mobile.feature.wird.model.DailyCount
import com.quran.mobile.feature.wird.model.RecordResult
import com.quran.mobile.feature.wird.model.ReminderMode
import com.quran.mobile.feature.wird.model.UnlockedAchievement
import com.quran.mobile.feature.wird.model.WirdSettings
import com.quran.mobile.feature.wird.model.WirdSummary
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Persistence for the daily wird: page views per day, khatma progress, unlocked achievements and
 * the user's goal / reminder settings. All reads are exposed as flows so the UI updates live.
 */
@SingleIn(AppScope::class)
class WirdRepository @Inject constructor(
  private val database: WirdDatabase,
  private val clock: WirdClock,
  private val mushafPageCount: MushafPageCount
) {
  private val queries: WirdQueries get() = database.wirdQueries

  val settings: Flow<WirdSettings> =
    queries.settings()
      .asFlow()
      .mapToOneOrNull(Dispatchers.IO)
      .map { it?.toModel() ?: WirdSettings.DEFAULT }

  val summary: Flow<WirdSummary> = combine(
    queries.dailyCounts().asFlow().mapToList(Dispatchers.IO),
    queries.unlockedAchievements().asFlow().mapToList(Dispatchers.IO),
    queries.completedKhatmas().asFlow().mapToList(Dispatchers.IO),
    settings
  ) { counts, unlocked, khatmas, settings ->
    withContext(Dispatchers.IO) {
      buildSummary(counts.map { it.toModel() }, unlocked, khatmas, settings)
    }
  }

  suspend fun currentSettings(): WirdSettings = withContext(Dispatchers.IO) {
    queries.settings().executeAsOneOrNull()?.toModel() ?: WirdSettings.DEFAULT
  }

  suspend fun currentSummary(): WirdSummary = withContext(Dispatchers.IO) {
    buildSummary(
      counts = queries.dailyCounts().executeAsList().map { it.toModel() },
      unlocked = queries.unlockedAchievements().executeAsList(),
      khatmas = queries.completedKhatmas().executeAsList(),
      settings = queries.settings().executeAsOneOrNull()?.toModel() ?: WirdSettings.DEFAULT
    )
  }

  /** First reading minute of each of the most recent [days] days that had any reading, newest first. */
  suspend fun recentFirstMinutes(days: Int): List<Int> = withContext(Dispatchers.IO) {
    queries.recentFirstMinutes(days.toLong()).executeAsList().mapNotNull { it.first_minute?.toInt() }
  }

  suspend fun updateSettings(transform: (WirdSettings) -> WirdSettings): WirdSettings =
    withContext(Dispatchers.IO) {
      database.transactionWithResult {
        val current = queries.settings().executeAsOneOrNull()?.toModel() ?: WirdSettings.DEFAULT
        val updated = transform(current).let {
          it.copy(
            pagesPerDay = it.pagesPerDay.coerceIn(WirdSettings.MIN_PAGES_PER_DAY, WirdSettings.MAX_PAGES_PER_DAY),
            reminderMinuteOfDay = it.reminderMinuteOfDay.coerceIn(0, 24 * 60 - 1)
          )
        }
        queries.upsertSettings(
          pages_per_day = updated.pagesPerDay.toLong(),
          reminder_enabled = if (updated.reminderEnabled) 1L else 0L,
          reminder_minute_of_day = updated.reminderMinuteOfDay.toLong(),
          reminder_mode = updated.reminderMode.storedValue
        )
        updated
      }
    }

  /**
   * Records that [page] was read now. Returns which achievements were newly unlocked and whether
   * this page completed a khatma. Re-recording a page already counted today is a no-op.
   */
  suspend fun recordPageView(page: Int): RecordResult = withContext(Dispatchers.IO) {
    val now = clock.nowMillis()
    val day = clock.dayOf(now)
    val minuteOfDay = clock.minuteOfDayOf(now)

    database.transactionWithResult {
      queries.insertPageView(day.toLong(), page.toLong(), now, minuteOfDay.toLong())
      if (queries.changes().executeAsOne() == 0L) {
        return@transactionWithResult RecordResult.NONE
      }

      // khatma progress
      val khatmaNumber = queries.currentKhatmaNumber().executeAsOne()
      queries.insertKhatmaPage(khatmaNumber, page.toLong())
      val totalPages = mushafPageCount.totalPages()
      val khatmaPages = queries.khatmaPageCount(khatmaNumber).executeAsOne()
      val completedKhatma = if (totalPages > 0 && khatmaPages >= totalPages) {
        queries.completeKhatma(khatmaNumber, now)
        khatmaNumber.toInt()
      } else {
        null
      }

      // achievements
      val settings = queries.settings().executeAsOneOrNull()?.toModel() ?: WirdSettings.DEFAULT
      val counts = queries.dailyCounts().executeAsList().map { it.toModel() }
      val streak = StreakCalculator.compute(
        counts.associate { it.day to it.pages },
        settings.pagesPerDay,
        day
      )
      val stats = WirdStats(
        totalPagesRead = queries.totalPages().executeAsOne().toInt(),
        daysGoalMet = counts.count { it.pages >= settings.pagesPerDay },
        bestStreak = streak.best,
        bestDayPages = counts.maxOfOrNull { it.pages } ?: 0,
        earlyMorningViews = queries.earlyMorningViews(
          AchievementEvaluator.FAJR_WINDOW_START_MINUTE.toLong(),
          AchievementEvaluator.FAJR_WINDOW_END_MINUTE.toLong()
        ).executeAsOne().toInt(),
        completedKhatmas = queries.completedKhatmas().executeAsList().size
      )
      val alreadyUnlocked = queries.unlockedAchievements()
        .executeAsList()
        .mapNotNull { Achievement.fromId(it.id) }
        .toSet()
      val newlyUnlocked = (AchievementEvaluator.evaluate(stats) - alreadyUnlocked).sortedBy { it.ordinal }
      newlyUnlocked.forEach { queries.unlockAchievement(it.id, now) }

      RecordResult(
        wasNewPageToday = true,
        newAchievements = newlyUnlocked,
        completedKhatmaNumber = completedKhatma
      )
    }
  }

  suspend fun clearProgress() = withContext(Dispatchers.IO) {
    queries.clearProgress()
  }

  private fun buildSummary(
    counts: List<DailyCount>,
    unlocked: List<Wird_achievement>,
    khatmas: List<Wird_khatma>,
    settings: WirdSettings
  ): WirdSummary {
    val today = clock.today()
    val streak = StreakCalculator.compute(
      counts.associate { it.day to it.pages },
      settings.pagesPerDay,
      today
    )
    val khatmaNumber = khatmas.size + 1
    val khatmaPages = queries.khatmaPageCount(khatmaNumber.toLong()).executeAsOne().toInt()

    return WirdSummary(
      today = today,
      todayPages = counts.firstOrNull { it.day == today }?.pages ?: 0,
      goal = settings.pagesPerDay,
      currentStreak = streak.current,
      bestStreak = streak.best,
      isTodayGoalMet = streak.isTodayMet,
      totalPagesRead = counts.sumOf { it.pages },
      khatmaNumber = khatmaNumber,
      khatmaPagesRead = khatmaPages,
      mushafPageCount = mushafPageCount.totalPages(),
      completedKhatmas = khatmas.map { CompletedKhatma(it.number.toInt(), it.completed_at) },
      unlockedAchievements = unlocked.mapNotNull { row ->
        Achievement.fromId(row.id)?.let { UnlockedAchievement(it, row.unlocked_at) }
      },
      recentDays = counts.filter { it.day > today - RECENT_DAYS }.sortedByDescending { it.day }
    )
  }

  private fun Wird_settings.toModel(): WirdSettings = WirdSettings(
    pagesPerDay = pages_per_day.toInt(),
    reminderEnabled = reminder_enabled != 0L,
    reminderMinuteOfDay = reminder_minute_of_day.toInt(),
    reminderMode = ReminderMode.fromStoredValue(reminder_mode)
  )

  private fun DailyCounts.toModel(): DailyCount =
    DailyCount(day = day.toInt(), pages = pages.toInt(), firstMinuteOfDay = first_minute?.toInt() ?: 0)

  companion object {
    const val RECENT_DAYS = 30
  }
}
