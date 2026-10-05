package com.quran.page.common.draw

/**
 * An [ImageDrawHelper] that needs to run before or after the others. Helpers are sorted by
 * [drawOrder] (ascending) before drawing; helpers that do not implement this interface draw at
 * order 0. A negative order means the helper draws right after the page image and *under* the
 * ayah highlights (selection, audio, bookmarks); a helper that repaints parts of the page (e.g.
 * the tajweed overlay) uses that so the highlights keep covering the text as on the original page.
 */
interface OrderedImageDrawHelper : ImageDrawHelper {
  val drawOrder: Int
}
