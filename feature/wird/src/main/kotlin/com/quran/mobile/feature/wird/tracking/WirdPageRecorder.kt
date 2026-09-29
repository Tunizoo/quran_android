package com.quran.mobile.feature.wird.tracking

import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.feature.wird.data.WirdRepository
import com.quran.mobile.feature.wird.reminder.WirdNotifier
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Entry point the reader uses to report the page currently on screen. A page only counts toward
 * the wird after it stays visible for [dwellTime], so flipping through pages to reach a position
 * does not inflate the count.
 */
@SingleIn(AppScope::class)
class WirdPageRecorder @Inject constructor(
  private val repository: WirdRepository,
  private val notifier: WirdNotifier,
  private val appCoroutineScope: AppCoroutineScope
) {
  private val lock = Any()
  private var pending: Job? = null
  private var pendingPage: Int? = null

  var dwellTime: Duration = DEFAULT_DWELL_TIME

  fun onPageVisible(page: Int) {
    synchronized(lock) {
      if (pendingPage == page && pending?.isActive == true) return
      pending?.cancel()
      pendingPage = page
      pending = appCoroutineScope.launch {
        delay(dwellTime)
        record(page)
      }
    }
  }

  fun onReaderHidden() {
    synchronized(lock) {
      pending?.cancel()
      pending = null
      pendingPage = null
    }
  }

  private suspend fun record(page: Int) {
    val result = repository.recordPageView(page)
    result.completedKhatmaNumber?.let { notifier.showKhatmaCompleted(it) }
    if (result.newAchievements.isNotEmpty()) {
      notifier.showAchievements(result.newAchievements)
    }
  }

  companion object {
    val DEFAULT_DWELL_TIME = 15.seconds
  }
}
