package com.quran.labs.androidquran.feature.reading.bridge

import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.feature.reading.model.LatestPageTracker
import com.quran.mobile.feature.wird.reminder.WirdReminderScheduler
import com.quran.mobile.feature.wird.tracking.WirdPageRecorder
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Feeds the page currently shown in the reader into the daily wird tracker and makes sure the
 * reminder schedule reflects the saved settings when the app starts.
 */
@SingleIn(AppScope::class)
class WirdReadingBridge @Inject constructor(
  private val latestPageTracker: LatestPageTracker,
  private val wirdPageRecorder: WirdPageRecorder,
  private val wirdReminderScheduler: WirdReminderScheduler,
  private val appCoroutineScope: AppCoroutineScope
) {
  private var isStarted = false

  fun start() {
    if (isStarted) return
    isStarted = true

    latestPageTracker.latestPage
      .filterNotNull()
      .onEach { wirdPageRecorder.onPageVisible(it.page) }
      .launchIn(appCoroutineScope)

    appCoroutineScope.launch { wirdReminderScheduler.reschedule() }
  }
}
