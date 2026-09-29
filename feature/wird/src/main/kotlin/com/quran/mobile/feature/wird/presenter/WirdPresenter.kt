package com.quran.mobile.feature.wird.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.quran.data.di.AppCoroutineScope
import com.quran.mobile.feature.wird.data.WirdRepository
import com.quran.mobile.feature.wird.model.WirdSettings
import com.quran.mobile.feature.wird.reminder.WirdReminderScheduler
import com.quran.mobile.feature.wird.state.WirdEvent
import com.quran.mobile.feature.wird.state.WirdState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

class WirdPresenter @Inject constructor(
  private val repository: WirdRepository,
  private val reminderScheduler: WirdReminderScheduler,
  private val appCoroutineScope: AppCoroutineScope
) {

  @Composable
  fun present(): WirdState {
    val summary by repository.summary.collectAsState(initial = null)
    val settings by repository.settings.collectAsState(initial = WirdSettings.DEFAULT)

    val eventSink: (WirdEvent) -> Unit = { event ->
      val transform: (WirdSettings) -> WirdSettings = when (event) {
        is WirdEvent.SetGoal -> { current -> current.copy(pagesPerDay = event.pagesPerDay) }
        is WirdEvent.SetReminderEnabled -> { current -> current.copy(reminderEnabled = event.enabled) }
        is WirdEvent.SetReminderTime -> { current -> current.copy(reminderMinuteOfDay = event.minuteOfDay) }
        is WirdEvent.SetReminderMode -> { current -> current.copy(reminderMode = event.mode) }
      }
      appCoroutineScope.launch {
        repository.updateSettings(transform)
        reminderScheduler.reschedule()
      }
    }

    return WirdState(summary = summary, settings = settings, eventSink = eventSink)
  }
}
