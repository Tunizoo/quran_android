package com.quran.mobile.feature.memorization.model

import com.quran.data.model.SuraAyah

/** A contiguous portion of the mushaf, from [start] to [end] inclusive. */
data class AyahRange(val start: SuraAyah, val end: SuraAyah) {
  val isSingleSura: Boolean get() = start.sura == end.sura
}

/** A portion being memorised and its spaced-repetition schedule. */
data class MemoItem(
  val id: Long,
  val range: AyahRange,
  val ease: Double,
  val intervalDays: Int,
  val repetitions: Int,
  val dueDay: Int,
  val lastReviewDay: Int?,
  val createdAt: Long
) {
  fun isDue(today: Int): Boolean = dueDay <= today
}

/** How the user rated a self-test; mapped to SM-2 quality levels. */
enum class Grade(val quality: Int) {
  AGAIN(1),
  HARD(3),
  GOOD(4),
  EASY(5)
}

data class MemorizationSummary(
  val today: Int,
  val items: List<MemoItem>,
  val dueToday: List<MemoItem>,
  val totalReviews: Long,
  val reviewsToday: Long
)
