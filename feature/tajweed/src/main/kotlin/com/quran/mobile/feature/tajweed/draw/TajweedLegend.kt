package com.quran.mobile.feature.tajweed.draw

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import com.quran.mobile.feature.tajweed.model.TajweedColorGroup
import java.util.Locale
import kotlin.math.ceil

/**
 * Draws the colour legend (a row of swatches with labels, right to left) under the page image, or
 * over its bottom edge when the view leaves no room below it.
 */
class TajweedLegend(context: Context) {
  private val density = context.resources.displayMetrics.density
  // the legend is always in Arabic, like the legend printed in the tajweed mushaf, whatever the
  // app language; the settings screen keeps the localized explanation
  private val arabic: Context = context.createConfigurationContext(
    Configuration(context.resources.configuration).apply { setLocale(Locale("ar")) }
  )
  private val labels: Map<TajweedColorGroup, String> =
    TajweedColorGroup.entries.associateWith { arabic.getString(it.labelResId) }

  private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 15f * density
    isFakeBoldText = true
  }
  private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

  private val swatch = 18f * density
  private val gap = 7f * density
  private val itemGap = 22f * density
  private val rowHeight = 30f * density
  private val sidePadding = 12f * density

  /** Height the legend needs for the given available width. */
  fun heightFor(width: Float): Float = rows(width).size * rowHeight + 8f * density

  private fun rows(width: Float): List<List<TajweedColorGroup>> {
    val usable = width - 2 * sidePadding
    val rows = ArrayList<MutableList<TajweedColorGroup>>()
    var current = ArrayList<TajweedColorGroup>()
    var used = 0f
    for (group in TajweedColorGroup.entries) {
      val w = itemWidth(group)
      if (current.isNotEmpty() && used + itemGap + w > usable) {
        rows.add(current)
        current = ArrayList()
        used = 0f
      }
      used += (if (current.isEmpty()) 0f else itemGap) + w
      current.add(group)
    }
    if (current.isNotEmpty()) rows.add(current)
    return rows
  }

  private fun itemWidth(group: TajweedColorGroup): Float =
    swatch + gap + textPaint.measureText(labels.getValue(group))

  /**
   * Draws the legend. [imageBottom] is the bottom edge of the page image inside the view; the
   * legend is placed below it when there is room, otherwise over the bottom of the page with a
   * translucent backdrop. Colours follow [night].
   */
  fun draw(canvas: Canvas, viewWidth: Float, viewHeight: Float, imageBottom: Float, night: Boolean, textColor: Int) {
    val rows = rows(viewWidth)
    val height = rows.size * rowHeight + 8f * density
    val spaceBelow = viewHeight - imageBottom
    val top: Float
    if (spaceBelow >= height + 4f * density) {
      top = imageBottom + 4f * density
    } else {
      top = viewHeight - height
      backdropPaint.color = if (night) 0xB3000000.toInt() else 0xCCFFFFFF.toInt()
      canvas.drawRect(0f, top, viewWidth, viewHeight, backdropPaint)
    }
    textPaint.color = textColor
    val fm = textPaint.fontMetrics
    var y = top + 4f * density
    for (row in rows) {
      // lay the row out from the right edge, in the order of the legend
      var x = viewWidth - sidePadding
      val textBaseline = y + rowHeight / 2f - (fm.ascent + fm.descent) / 2f
      for (group in row) {
        swatchPaint.color = group.color(night)
        val swatchTop = y + (rowHeight - swatch) / 2f
        canvas.drawRoundRect(
          RectF(x - swatch, swatchTop, x, swatchTop + swatch), 2f * density, 2f * density, swatchPaint
        )
        val label = labels.getValue(group)
        val labelWidth = textPaint.measureText(label)
        canvas.drawText(label, x - swatch - gap - labelWidth, textBaseline, textPaint)
        x -= ceil(itemWidth(group)) + itemGap
      }
      y += rowHeight
    }
  }
}
