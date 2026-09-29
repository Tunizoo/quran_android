package com.quran.mobile.feature.wird.state

import com.quran.mobile.feature.wird.model.ReminderMode
import com.quran.mobile.feature.wird.model.WirdSettings
import com.quran.mobile.feature.wird.model.WirdSummary

sealed interface WirdEvent {
  data class SetGoal(val pagesPerDay: Int) : WirdEvent
  data class SetReminderEnabled(val enabled: Boolean) : WirdEvent
  data class SetReminderTime(val minuteOfDay: Int) : WirdEvent
  data class SetReminderMode(val mode: ReminderMode) : WirdEvent
}

data class WirdState(
  /** null while the first summary is loading */
  val summary: WirdSummary?,
  val settings: WirdSettings,
  val eventSink: (WirdEvent) -> Unit
)
