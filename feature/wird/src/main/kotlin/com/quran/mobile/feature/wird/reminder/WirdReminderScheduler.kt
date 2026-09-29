package com.quran.mobile.feature.wird.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.wird.data.WirdRepository
import com.quran.mobile.feature.wird.domain.ReminderTimeCalculator
import com.quran.mobile.feature.wird.domain.WirdClock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.TimeUnit

/**
 * Schedules the next daily reminder as a one-time WorkManager job. The worker re-schedules itself
 * after running, and [reschedule] is also called on app start and whenever settings change so the
 * next run always reflects the latest goal, time, and (in adaptive mode) reading habits.
 */
@SingleIn(AppScope::class)
class WirdReminderScheduler @Inject constructor(
  @ApplicationContext private val context: Context,
  private val repository: WirdRepository,
  private val clock: WirdClock
) {
  suspend fun reschedule() {
    val workManager = try {
      WorkManager.getInstance(context)
    } catch (e: IllegalStateException) {
      // WorkManager is not initialized (e.g. under test) - nothing to schedule.
      return
    }

    val settings = repository.currentSettings()
    if (!settings.reminderEnabled) {
      workManager.cancelUniqueWork(WORK_NAME)
      return
    }

    val recentFirstMinutes = repository.recentFirstMinutes(ReminderTimeCalculator.ADAPTIVE_HISTORY_DAYS)
    val targetMinute = ReminderTimeCalculator.targetMinuteOfDay(settings, recentFirstMinutes)
    val delayMillis = ReminderTimeCalculator.delayUntilNext(clock.nowMillis(), targetMinute)

    val request = OneTimeWorkRequestBuilder<WirdReminderWorker>()
      .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
      .addTag(WORK_TAG)
      .build()
    workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
  }

  companion object {
    const val WORK_NAME = "wird_daily_reminder"
    const val WORK_TAG = "wird"
  }
}
