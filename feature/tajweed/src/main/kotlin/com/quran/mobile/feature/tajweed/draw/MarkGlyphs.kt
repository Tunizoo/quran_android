package com.quran.mobile.feature.tajweed.draw

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.fonts.Font
import android.graphics.text.PositionedGlyphs
import android.graphics.text.TextRunShaper
import android.os.Build
import android.text.TextPaint
import androidx.annotation.RequiresApi
import kotlin.math.abs

/**
 * Finds the glyphs of some combining marks inside a shaped word so they can be repainted in another
 * colour. A base letter and its marks are one cluster, which the text renderer draws in a single
 * colour; the shaper however still produces a separate glyph for the mark (unless the font merges
 * letter and mark into one glyph).
 *
 * The mark's glyphs are found by shaping the word twice, with and without the mark: the glyphs that
 * only exist in the first run are the mark. When removing the mark changes anything else (the font
 * has contextual forms that depend on marks), the result is discarded and the caller falls back to
 * colouring the whole cluster. The word itself is never drawn from the shortened text.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal object MarkGlyphs {

  class Result(val glyphIds: IntArray, val positions: FloatArray, val font: Font)

  /**
   * Glyphs of the marks at [markStart, markEnd) of [text], positioned relative to the start of the
   * run on its baseline, or null when they cannot be isolated.
   */
  fun find(text: String, markStart: Int, markEnd: Int, paint: TextPaint): Result? {
    return try {
      val full = TextRunShaper.shapeTextRun(text, 0, text.length, 0, text.length, 0f, 0f, true, paint)
      val reducedText = text.removeRange(markStart, markEnd)
      val reduced = TextRunShaper.shapeTextRun(reducedText, 0, reducedText.length, 0, reducedText.length, 0f, 0f, true, paint)
      diff(full, reduced, markEnd - markStart)
    } catch (e: RuntimeException) {
      null
    }
  }

  private fun diff(full: PositionedGlyphs, reduced: PositionedGlyphs, removedChars: Int): Result? {
    val fullCount = full.glyphCount()
    val reducedCount = reduced.glyphCount()
    if (fullCount <= reducedCount) return null // the mark has no glyph of its own (merged by the font)

    val used = BooleanArray(reducedCount)
    val ids = ArrayList<Int>()
    val positions = ArrayList<Float>()
    var font: Font? = null
    for (i in 0 until fullCount) {
      val id = full.getGlyphId(i)
      val x = full.getGlyphX(i)
      val y = full.getGlyphY(i)
      var matched = false
      for (j in 0 until reducedCount) {
        if (!used[j] && reduced.getGlyphId(j) == id &&
          abs(reduced.getGlyphX(j) - x) < TOLERANCE && abs(reduced.getGlyphY(j) - y) < TOLERANCE
        ) {
          used[j] = true
          matched = true
          break
        }
      }
      if (!matched) {
        ids.add(id)
        positions.add(x)
        positions.add(y)
        val glyphFont = full.getFont(i)
        if (font != null && font != glyphFont) return null
        font = glyphFont
      }
    }
    // every glyph of the shortened word must exist unchanged in the full word, and only the removed
    // marks may be extra; otherwise removing the mark reshaped the word
    if (used.any { !it }) return null
    if (ids.isEmpty() || ids.size > removedChars) return null
    return Result(ids.toIntArray(), positions.toFloatArray(), font ?: return null)
  }

  fun draw(canvas: Canvas, glyphIds: IntArray, positions: FloatArray, font: Font, color: Int, textPaint: TextPaint) {
    val paint = Paint(textPaint)
    paint.color = color
    canvas.drawGlyphs(glyphIds, 0, positions, 0, glyphIds.size, font, paint)
  }

  private const val TOLERANCE = 0.5f
}
