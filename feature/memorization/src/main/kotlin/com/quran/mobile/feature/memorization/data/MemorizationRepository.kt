package com.quran.mobile.feature.memorization.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.di.RewardEvent
import com.quran.mobile.di.RewardSink
import com.quran.mobile.feature.memorization.db.Memo_item
import com.quran.mobile.feature.memorization.db.MemorizationDatabase
import com.quran.mobile.feature.memorization.domain.MemorizationClock
import com.quran.mobile.feature.memorization.domain.Schedule
import com.quran.mobile.feature.memorization.domain.Sm2Scheduler
import com.quran.mobile.feature.memorization.model.AyahRange
import com.quran.mobile.feature.memorization.model.Grade
import com.quran.mobile.feature.memorization.model.MemoItem
import com.quran.mobile.feature.memorization.model.MemorizationSummary
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Portions being memorised, their reviews and the spaced-repetition schedule. */
@SingleIn(AppScope::class)
class MemorizationRepository @Inject constructor(
  private val database: MemorizationDatabase,
  private val clock: MemorizationClock,
  private val rewardSink: RewardSink
) {
  private val queries get() = database.memorizationQueries

  val items: Flow<List<MemoItem>> =
    queries.allItems().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toModel() } }

  /** Number of portions due today or earlier. Re-evaluated whenever the table changes. */
  val dueCount: Flow<Int> =
    queries.dueCount(clock.today().toLong()).asFlow().mapToOne(Dispatchers.IO).map { it.toInt() }

  val summary: Flow<MemorizationSummary> = combine(
    items,
    queries.reviewCount().asFlow().mapToOne(Dispatchers.IO),
    queries.reviewCountForDay(clock.today().toLong()).asFlow().mapToOne(Dispatchers.IO)
  ) { items, reviews, reviewsToday ->
    val today = clock.today()
    MemorizationSummary(
      today = today,
      items = items,
      dueToday = items.filter { it.isDue(today) },
      totalReviews = reviews,
      reviewsToday = reviewsToday
    )
  }

  suspend fun item(id: Long): MemoItem? = withContext(Dispatchers.IO) {
    queries.itemById(id).executeAsOneOrNull()?.toModel()
  }

  /** Adds a portion due today, or returns the existing item covering exactly the same range. */
  suspend fun addItem(range: AyahRange): MemoItem = withContext(Dispatchers.IO) {
    val existing = queries.allItems().executeAsList().firstOrNull {
      it.start_sura.toInt() == range.start.sura && it.start_ayah.toInt() == range.start.ayah &&
        it.end_sura.toInt() == range.end.sura && it.end_ayah.toInt() == range.end.ayah
    }
    if (existing != null) return@withContext existing.toModel()
    val initial = Schedule.INITIAL
    val id = database.transactionWithResult {
      queries.insertItem(
        start_sura = range.start.sura.toLong(),
        start_ayah = range.start.ayah.toLong(),
        end_sura = range.end.sura.toLong(),
        end_ayah = range.end.ayah.toLong(),
        ease = initial.ease,
        interval_days = initial.intervalDays.toLong(),
        repetitions = initial.repetitions.toLong(),
        due_day = clock.today().toLong(),
        created_at = clock.nowMillis()
      )
      queries.lastInsertId().executeAsOne()
    }
    rewardSink.earn(RewardEvent.MEMO_PORTION_ADDED, id.toString())
    queries.itemById(id).executeAsOne().toModel()
  }

  /** Records a self-test result and reschedules the portion. Returns the updated item. */
  suspend fun review(itemId: Long, grade: Grade): MemoItem? = withContext(Dispatchers.IO) {
    val row = queries.itemById(itemId).executeAsOneOrNull() ?: return@withContext null
    val today = clock.today()
    val next = Sm2Scheduler.next(
      Schedule(row.ease, row.interval_days.toInt(), row.repetitions.toInt()),
      grade.quality
    )
    database.transaction {
      queries.updateSchedule(
        ease = next.ease,
        interval_days = next.intervalDays.toLong(),
        repetitions = next.repetitions.toLong(),
        due_day = (today + next.intervalDays).toLong(),
        last_review_day = today.toLong(),
        id = itemId
      )
      queries.insertReview(itemId, today.toLong(), grade.quality.toLong(), clock.nowMillis())
    }
    val event = when (grade) {
      Grade.AGAIN -> RewardEvent.MEMO_REVIEW_AGAIN
      Grade.HARD -> RewardEvent.MEMO_REVIEW_HARD
      Grade.GOOD -> RewardEvent.MEMO_REVIEW_GOOD
      Grade.EASY -> RewardEvent.MEMO_REVIEW_EASY
    }
    rewardSink.earn(event, itemId.toString())
    queries.itemById(itemId).executeAsOne().toModel()
  }

  suspend fun deleteItem(itemId: Long) = withContext(Dispatchers.IO) {
    database.transaction {
      queries.deleteReviewsOfItem(itemId)
      queries.deleteItem(itemId)
    }
  }

  private fun Memo_item.toModel(): MemoItem = MemoItem(
    id = id,
    range = AyahRange(
      SuraAyah(start_sura.toInt(), start_ayah.toInt()),
      SuraAyah(end_sura.toInt(), end_ayah.toInt())
    ),
    ease = ease,
    intervalDays = interval_days.toInt(),
    repetitions = repetitions.toInt(),
    dueDay = due_day.toInt(),
    lastReviewDay = last_review_day?.toInt(),
    createdAt = created_at
  )
}
