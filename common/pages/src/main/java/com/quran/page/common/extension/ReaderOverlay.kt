package com.quran.page.common.extension

import android.app.Activity
import android.view.View
import com.quran.data.model.SuraAyah

/** What the reader (the page viewer activity) offers to an overlay contributed by a feature. */
interface ReaderHost {
  val activity: Activity

  /** The page currently shown (the first one when two pages are visible). */
  fun currentPage(): Int

  /** The ayah range currently selected on the page, if any. */
  fun selectedAyahRange(): Pair<SuraAyah, SuraAyah>?

  /** Clears the ayah selection and its toolbar. */
  fun clearSelection()

  /**
   * Plays [start]..[end] with the current qari. [verseRepeat] is the number of extra times each
   * ayah is repeated (0 = play once, -1 = forever); [rangeRepeat] likewise for the whole range.
   */
  fun playRange(start: SuraAyah, end: SuraAyah, verseRepeat: Int, rangeRepeat: Int)

  /** Redraws the visible pages (e.g. after an overlay changed what it paints on them). */
  fun refreshPages()
}

/** A view a feature places over the reader, at the bottom of the screen above the audio bar. */
interface ReaderOverlay {
  val view: View

  /** Called when the overlay's entry in the reader overflow menu is selected. */
  fun onMenuSelected()

  fun onPageChanged(page: Int)

  fun onDestroy()
}

/**
 * Extension point for features that add a control overlay and an overflow menu entry to the
 * reader. Contributed into a set at the application scope; the reader creates one overlay per
 * provider when it starts.
 */
interface ReaderOverlayProvider {
  val order: Int

  /** Title of the entry added to the reader overflow menu, or 0 for no entry. */
  val menuTitleResId: Int

  fun createOverlay(host: ReaderHost): ReaderOverlay
}
