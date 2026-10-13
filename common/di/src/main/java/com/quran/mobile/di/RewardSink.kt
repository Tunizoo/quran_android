package com.quran.mobile.di

/**
 * Things the user is rewarded points for. The names are persisted as the source of each points
 * entry, so never rename an existing entry.
 */
enum class RewardEvent(val points: Int) {
  PAGE_READ(10),
  DAILY_GOAL(50),
  KHATMA(500),
  ACHIEVEMENT(100),
  WEEKLY_CHALLENGE(150),
  MEMO_PORTION_ADDED(20),
  MEMO_REVIEW_AGAIN(5),
  MEMO_REVIEW_HARD(15),
  MEMO_REVIEW_GOOD(30),
  MEMO_REVIEW_EASY(40);

  val isMemorizationReview: Boolean get() = name.startsWith("MEMO_REVIEW_")
}

/**
 * Where features report rewardable activity. The daily wird feature implements it (points,
 * levels, weekly challenge, achievements); other features such as memorization only call it.
 */
interface RewardSink {
  /**
   * Awards the points of [event]. [ref] makes the award idempotent for events that must count once
   * (e.g. the daily goal of a given day); events without a ref are always counted.
   */
  fun earn(event: RewardEvent, ref: String? = null)
}
