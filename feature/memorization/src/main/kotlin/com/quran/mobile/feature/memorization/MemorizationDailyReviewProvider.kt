package com.quran.mobile.feature.memorization

import android.content.Context
import android.content.Intent
import com.quran.data.di.AppScope
import com.quran.mobile.di.DailyReviewProvider
import com.quran.mobile.feature.memorization.data.MemorizationRepository
import com.quran.mobile.feature.memorization.ui.MemorizationActivity
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** Shows the portions due for review today as a card on the daily wird screen. */
@ContributesIntoSet(AppScope::class)
class MemorizationDailyReviewProvider @Inject constructor(
  private val repository: MemorizationRepository
) : DailyReviewProvider {
  override val order: Int = 10
  override val titleResId: Int = R.string.memo_wird_card_title
  override val dueCount: Flow<Int> get() = repository.dueCount

  override fun describe(context: Context, count: Int): String =
    if (count == 0) context.getString(R.string.memo_wird_none)
    else context.resources.getQuantityString(R.plurals.memo_wird_due, count, count)

  override fun open(context: Context) {
    context.startActivity(Intent(context, MemorizationActivity::class.java))
  }
}
