package com.quran.mobile.feature.wird.domain

import com.quran.data.core.QuranInfo
import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Number of pages in the currently selected mushaf, used to detect a completed khatma. */
fun interface MushafPageCount {
  fun totalPages(): Int
}

@ContributesBinding(AppScope::class)
class QuranInfoMushafPageCount @Inject constructor(
  private val quranInfoProvider: () -> QuranInfo
) : MushafPageCount {
  override fun totalPages(): Int = quranInfoProvider().numberOfPages
}
