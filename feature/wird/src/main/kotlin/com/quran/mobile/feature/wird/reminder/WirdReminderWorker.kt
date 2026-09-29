package com.quran.mobile.feature.wird.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.wird.di.WirdComponentInterface

/**
 * Fires at the scheduled reminder time. Only notifies when today's goal has not been met yet
 * ("smart" reminder), then schedules the next occurrence.
 *
 * Instantiated reflectively by WorkManager's default factory, so dependencies are pulled from the
 * application graph rather than injected.
 */
class WirdReminderWorker(
  context: Context,
  parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

  override suspend fun doWork(): Result {
    val graph = (applicationContext as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? WirdComponentInterface
      ?: return Result.failure()

    val repository = graph.wirdRepository
    val settings = repository.currentSettings()
    if (settings.reminderEnabled) {
      val summary = repository.currentSummary()
      if (!summary.isTodayGoalMet) {
        graph.wirdNotifier.showReminder(summary)
      }
    }
    graph.wirdReminderScheduler.reschedule()
    return Result.success()
  }
}
