package com.quran.mobile.di

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * A feature with a daily task to surface on the daily wird screen (for example the portions due
 * for memorization review). Contributed into a set at the application scope.
 */
interface DailyReviewProvider {
  val order: Int

  /** Title of the card shown on the wird screen. */
  val titleResId: Int

  /** How many items are due today; the wird screen re-renders when it changes. */
  val dueCount: Flow<Int>

  /** Text for the card body: [count] items due today (0 means none). */
  fun describe(context: Context, count: Int): String

  /** Opens the feature's own screen. */
  fun open(context: Context)
}
