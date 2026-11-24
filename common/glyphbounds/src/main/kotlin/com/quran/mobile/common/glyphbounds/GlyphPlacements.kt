package com.quran.mobile.common.glyphbounds

import android.graphics.RectF
import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.ConcurrentHashMap

/**
 * Where the words of a page are really drawn, in page image pixels.
 *
 * A helper that sets the words of a page again (the tajweed colouring) does not put every word
 * exactly on its glyph rectangle: the words of a line are spaced evenly instead of being stretched
 * one by one. It records the rectangles here, and the helpers that draw on words or hit-test them
 * (highlights of the divine names, covers of the memorization mode) look here before falling back
 * to the glyph rectangle.
 */
@SingleIn(AppScope::class)
class GlyphPlacements @Inject constructor() {
  private data class Key(val page: Int, val width: Int)

  private val pages = ConcurrentHashMap<Key, Map<GlyphPosition, RectF>>()

  fun set(page: Int, width: Int, bounds: Map<GlyphPosition, RectF>) {
    pages[Key(page, width)] = bounds
  }

  /** Forgets every page: the words are back on their glyph rectangles. */
  fun clear() {
    if (pages.isNotEmpty()) pages.clear()
  }

  /** The moved words of [page], or null when the page is drawn as it is in the page image. */
  fun forPage(page: Int, width: Int): Map<GlyphPosition, RectF>? = pages[Key(page, width)]

  /** The rectangle [glyph] is drawn in on [page]. */
  fun boundsOf(page: Int, width: Int, glyph: GlyphRect): RectF =
    pages[Key(page, width)]?.get(glyph.position) ?: glyph.bounds
}
