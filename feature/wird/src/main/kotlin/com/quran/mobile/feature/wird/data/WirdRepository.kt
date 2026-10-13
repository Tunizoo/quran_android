package com.quran.mobile.feature.wird.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.quran.data.di.AppScope
import com.quran.mobile.di.RewardEvent
import com.quran.mobile.feature.wird.db.DailyCounts
import com.quran.mobile.feature.wird.db.WirdDatabase
import com.quran.mobile.feature.wird.db.WirdQueries
import com.quran.mobile.feature.wird.db.Wird_achievement
import com.quran.mobile.feature.wird.db.Wird_khatma
import com.quran.mobile.feature.wird.db.Wird_settings
import com.quran.mobile.feature.wird.domain.AchievementEvaluator
import com.quran.mobile.feature.wird.domain.ChallengeProgress
import com.quran.mobile.feature.wird.domain.ChallengeType
import com.quran.mobile.feature.wird.domain.LevelCalculator
import com.quran.mobile.feature.wird.domain.MushafPageCount
import com.quran.mobile.feature.wird.domain.StreakCalculator
import com.quran.mobile.feature.wird.domain.WeeklyChallenge
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
 * Persistence for the daily wird: page views per day, khatma progress, unlocked achievements, the
 * points ledger (levels, weekly challenge) and the user's goal / reminder settings. All reads are
 * exposed as flows so the UI updates live.
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
    settings,
    queries.totalPoints().asFlow().mapToOne(Dispatchers.IO)
  ) { counts, unlocked, khatmas, settings, _ ->
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
   * Records that [page] was read now. Returns which achievements were newly unlocked, whether this
   * page completed a khatma, and any level / challenge reached. Re-recording a page already
   * counted today is a no-op.
   */
  suspend fun recordPageView(page: Int): RecordResult = withContext(Dispatchers.IO) {
    val now = clock.nowMillis()
    val day = clock.dayOf(now)
    val minuteOfDay = clock.minuteOfDayOf(now)

    database.transactionWithResult {
      val pointsBefore = queries.totalPoints().executeAsOne().toInt()
      queries.insertPageView(day.toLong(), page.toLong(), now, minuteOfDay.toLong())
      if (queries.changes().executeAsOne() == 0L) {
        return@transactionWithResult RecordResult.NONE
      }
      award(RewardEvent.PAGE_READ, page.toString(), day, now)

      // daily goal reached with this page
      val settings = queries.settings().executeAsOneOrNull()?.toModel() ?: WirdSettings.DEFAULT
      if (queries.pagesForDay(day.toLong()).executeAsOne() >= settings.pagesPerDay) {
        award(RewardEvent.DAILY_GOAL, day.toString(), day, now)
      }

      // khatma progress
      val khatmaNumber = queries.currentKhatmaNumber().executeAsOne()
      queries.insertKhatmaPage(khatmaNumber, page.toLong())
      val totalPages = mushafPageCount.totalPages()
      val khatmaPages = queries.khatmaPageCount(khatmaNumber).executeAsOne()
      val completedKhatma = if (totalPages > 0 && khatmaPages >= totalPages) {
        queries.completeKhatma(khatmaNumber, now)
        award(RewardEvent.KHATMA, khatmaNumber.toString(), day, now)
        khatmaNumber.toInt()
      } else {
        null
      }

      val newlyUnlocked = unlockNewAchievements(day, now)
      val challengeCompleted = checkChallenge(day, now)
      val pointsAfter = queries.totalPoints().executeAsOne().toInt()

      RecordResult(
        wasNewPageToday = true,
        newAchievements = newlyUnlocked,
        completedKhatmaNumber = completedKhatma,
        levelReached = levelReached(pointsBefore, pointsAfter),
        challengeCompleted = challengeCompleted
      )
    }
  }

  /**
   * Awards the points of [event] (reported by another feature, e.g. a memorization review) and
   * re-evaluates achievements, the weekly challenge and the level.
   */
  suspend fun earn(event: RewardEvent, ref: String?): RecordResult = withContext(Dispatchers.IO) {
    val now = clock.nowMillis()
    val day = clock.dayOf(now)
    database.transactionWithResult {
      val pointsBefore = queries.totalPoints().executeAsOne().toInt()
      if (!award(event, ref, day, now)) {
        return@transactionWithResult RecordResult.NONE
      }
      val newlyUnlocked = unlockNewAchievements(day, now)
      val challengeCompleted = checkChallenge(day, now)
      val pointsAfter = queries.totalPoints().executeAsOne().toInt()
      RecordResult(
        wasNewPageToday = false,
        newAchievements = newlyUnlocked,
        completedKhatmaNumber = null,
        levelReached = levelReached(pointsBefore, pointsAfter),
        challengeCompleted = challengeCompleted
      )
    }
  }

  /**
   * Credits the reading done before the points ledger existed (pages, goal days, khatmas and
   * achievements), once, so that existing users do not start from zero after the update.
   */
  suspend fun backfillPoints() = withContext(Dispatchers.IO) {
    database.transaction {
      if (queries.totalPoints().executeAsOne() != 0L) return@transaction
      val counts = queries.dailyCounts().executeAsList()
      if (counts.isEmpty()) return@transaction
      val settings = queries.settings().executeAsOneOrNull()?.toModel() ?: WirdSettings.DEFAULT
      val now = clock.nowMillis()
      for (count in counts) {
        val day = count.day.toInt()
        repeat(count.pages.toInt()) { queries.insertPoints(day.toLong(), RewardEvent.PAGE_READ.name, RewardEvent.PAGE_READ.points.toLong(), null, now) }
        if (count.pages >= settings.pagesPerDay) award(RewardEvent.DAILY_GOAL, day.toString(), day, now)
      }
      val today = clock.today()
      queries.completedKhatmas().executeAsList().forEach { award(RewardEvent.KHATMA, it.number.toString(), today, now) }
      queries.unlockedAchievements().executeAsList().forEach { award(RewardEvent.ACHIEVEMENT, it.id, today, now) }
    }
  }

  suspend fun clearProgress() = withContext(Dispatchers.IO) {
    queries.clearProgress()
  }

  // ---------------------------------------------------------------------------------- rewards

  /** Inserts the points of [event]; events that must count once are skipped when already awarded. */
  private fun award(event: RewardEvent, ref: String?, day: Int, now: Long): Boolean {
    if (ref != null && event in ONCE_PER_REF && queries.hasPoints(event.name, ref).executeAsOne() > 0) {
      return false
    }
    queries.insertPoints(day.toLong(), event.name, event.points.toLong(), ref, now)
    return true
  }

  private fun levelReached(pointsBefore: Int, pointsAfter: Int): Int? {
    val before = LevelCalculator.levelFor(pointsBefore).number
    val after = LevelCalculator.levelFor(pointsAfter).number
    return if (after > before) after else null
  }

  /** Evaluates the achievements against the current statistics and unlocks (and rewards) new ones. */
  private fun unlockNewAchievements(day: Int, now: Long): List<Achievement> {
    val settings = queries.settings().executeAsOneOrNull()?.toModel() ?: WirdSettings.DEFAULT
    val counts = queries.dailyCounts().executeAsList().map { it.toModel() }
    val streak = StreakCalculator.compute(counts.associate { it.day to it.pages }, settings.pagesPerDay, day)
    val stats = WirdStats(
      totalPagesRead = queries.totalPages().executeAsOne().toInt(),
      daysGoalMet = counts.count { it.pages >= settings.pagesPerDay },
      bestStreak = streak.best,
      bestDayPages = counts.maxOfOrNull { it.pages } ?: 0,
      earlyMorningViews = queries.earlyMorningViews(
        AchievementEvaluator.FAJR_WINDOW_START_MINUTE.toLong(),
        AchievementEvaluator.FAJR_WINDOW_END_MINUTE.toLong()
      ).executeAsOne().toInt(),
      completedKhatmas = queries.completedKhatmas().executeAsList().size,
      memorizationPortions = memorizationPortions(),
      memorizationReviews = memorizationReviews()
    )
    val alreadyUnlocked = queries.unlockedAchievements()
      .executeAsList()
      .mapNotNull { Achievement.fromId(it.id) }
      .toSet()
    val newlyUnlocked = (AchievementEvaluator.evaluate(stats) - alreadyUnlocked).sortedBy { it.ordinal }
    newlyUnlocked.forEach {
      queries.unlockAchievement(it.id, now)
      award(RewardEvent.ACHIEVEMENT, it.id, day, now)
    }
    return newlyUnlocked
  }

  private fun memorizationPortions(): Int =
    queries.countSource(RewardEvent.MEMO_PORTION_ADDED.name).executeAsOne().toInt()

  private fun memorizationReviews(): Int = queries.countSourceLike(MEMO_REVIEW_PATTERN).executeAsOne().toInt()

  /** Progress of the challenge of the week containing [day]. */
  private fun challengeProgress(day: Int, settings: WirdSettings, counts: List<DailyCount>): ChallengeProgress {
    val week = WeeklyChallenge.weekOf(day)
    val challenge = WeeklyChallenge.forWeek(week, settings.pagesPerDay)
    val firstDay = WeeklyChallenge.firstDayOfWeek(week)
    val weekCounts = counts.filter { it.day >= firstDay && it.day < firstDay + 7 }
    val progress = when (challenge.type) {
      ChallengeType.PAGES -> weekCounts.sumOf { it.pages }
      ChallengeType.GOAL_DAYS -> weekCounts.count { it.pages >= settings.pagesPerDay }
      ChallengeType.REVIEWS ->
        queries.countSourceLikeSince(MEMO_REVIEW_PATTERN, firstDay.toLong()).executeAsOne().toInt()
    }
    val completed = queries.challengeForWeek(week.toLong()).executeAsOneOrNull() != null
    return ChallengeProgress(
      challenge = challenge,
      progress = progress,
      completed = completed || progress >= challenge.target,
      daysLeft = WeeklyChallenge.daysLeftInWeek(day)
    )
  }

  /** Marks the week's challenge completed (and rewards it) the first time its target is reached. */
  private fun checkChallenge(day: Int, now: Long): Boolean {
    val settings = queries.settings().executeAsOneOrNull()?.toModel() ?: WirdSettings.DEFAULT
    val counts = queries.dailyCounts().executeAsList().map { it.toModel() }
    val progress = challengeProgress(day, settings, counts)
    val week = progress.challenge.week.toLong()
    if (progress.progress < progress.challenge.target) return false
    if (queries.challengeForWeek(week).executeAsOneOrNull() != null) return false
    queries.completeChallenge(week, now)
    award(RewardEvent.WEEKLY_CHALLENGE, week.toString(), day, now)
    return true
  }

  // ---------------------------------------------------------------------------------- summary

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
    val points = queries.totalPoints().executeAsOne().toInt()

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
      recentDays = counts.filter { it.day > today - RECENT_DAYS }.sortedByDescending { it.day },
      level = LevelCalculator.levelFor(points),
      challenge = challengeProgress(today, settings, counts),
      memorizationPortions = memorizationPortions(),
      memorizationReviews = memorizationReviews()
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
    private const val MEMO_REVIEW_PATTERN = "MEMO_REVIEW_%"
    private val ONCE_PER_REF = setOf(
      RewardEvent.DAILY_GOAL, RewardEvent.KHATMA, RewardEvent.ACHIEVEMENT, RewardEvent.WEEKLY_CHALLENGE
    )
  }
}
