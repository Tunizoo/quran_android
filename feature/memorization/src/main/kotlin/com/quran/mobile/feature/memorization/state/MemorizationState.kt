package com.quran.mobile.feature.memorization.state

import com.quran.mobile.feature.memorization.model.AyahRange
import com.quran.mobile.feature.memorization.model.MemorizationSummary

sealed interface MemorizationEvent {
  data class AddPortion(val range: AyahRange) : MemorizationEvent
  data class DeletePortion(val itemId: Long) : MemorizationEvent
  /** Starts a review session of the portion and opens the reader at its first ayah. */
  data class Review(val itemId: Long) : MemorizationEvent
}

data class MemorizationState(
  /** null while the first summary is loading */
  val summary: MemorizationSummary?,
  val eventSink: (MemorizationEvent) -> Unit
)
