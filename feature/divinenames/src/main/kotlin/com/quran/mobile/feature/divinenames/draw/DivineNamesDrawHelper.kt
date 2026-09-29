package com.quran.mobile.feature.divinenames.draw

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.feature.divinenames.data.DivineNamesDatabase
import com.quran.mobile.feature.divinenames.data.DivineNamesSettings
import com.quran.mobile.feature.divinenames.data.GlyphBoundsSource
import com.quran.mobile.feature.divinenames.model.NameOccurrence
import com.quran.mobile.feature.divinenames.model.OccurrenceRect
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Paints a translucent highlight over every word of the page that is one of the names of Allah
 * (gold) or one of His attributes (yellow).
 *
 * The rectangles for a page are loaded once in the background (from the bundled occurrence table
 * and the page glyph database) and cached, so drawing is a plain loop over cached rectangles. The
 * same cache answers hit tests for the double tap that opens the explanation sheet. Occurrence
 * positions were generated for the madani mushaf layout, so the helper is inactive for other page
 * types.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class DivineNamesDrawHelper @Inject constructor(
  private val settings: DivineNamesSettings,
  private val database: DivineNamesDatabase,
  private val glyphBoundsSource: GlyphBoundsSource,
  private val quranInfoProvider: () -> QuranInfo,
  private val appCoroutineScope: AppCoroutineScope,
  @Named(DependencyInjectionConstants.CURRENT_PAGE_TYPE) private val pageType: String
) : ImageDrawHelper {

  private data class Key(val page: Int, val width: Int)

  private val cache: MutableMap<Key, List<OccurrenceRect>> = Collections.synchronizedMap(HashMap())
  private val loading: MutableSet<Key> = Collections.synchronizedSet(HashSet())

  private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = NAME_COLOR
    style = Paint.Style.FILL
  }
  private val attributePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = ATTRIBUTE_COLOR
    style = Paint.Style.FILL
  }
  private val scaledRect = RectF()

  val isActive: Boolean get() = settings.isHighlightEnabled && pageType == SUPPORTED_PAGE_TYPE

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    if (!isActive) return
    val width = imageWidth(image.drawable) ?: return

    val key = Key(pageCoordinates.page, width)
    val rects = cache[key]
    if (rects == null) {
      load(key, image)
      return
    }

    val radius = image.imageMatrix.mapRadius(CORNER_RADIUS_PX)
    for (item in rects) {
      image.imageMatrix.mapRect(scaledRect, item.bounds)
      scaledRect.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
      val paint = if (item.occurrence.isAttribute) attributePaint else namePaint
      canvas.drawRoundRect(scaledRect, radius, radius, paint)
    }
  }

  /**
   * The highlighted occurrence under ([pageX], [pageY]) in page image pixels, or null when the
   * point is not on a highlighted word (or the page's rectangles are not loaded yet).
   */
  fun occurrenceAt(page: Int, image: ImageView, pageX: Float, pageY: Float): NameOccurrence? {
    if (!isActive) return null
    val width = imageWidth(image.drawable) ?: return null
    val rects = cache[Key(page, width)] ?: return null
    return rects.firstOrNull { it.bounds.contains(pageX, pageY) }?.occurrence
  }

  private fun imageWidth(drawable: Drawable?): Int? {
    if (drawable == null) return null
    // the drawable reports a density-scaled size; the ayahinfo database is keyed by the real
    // pixel width of the page image, so prefer the bitmap dimensions when available
    val width = (drawable as? BitmapDrawable)?.bitmap?.width ?: drawable.intrinsicWidth
    return if (width > 0) width else null
  }

  private fun load(key: Key, image: ImageView) {
    if (!loading.add(key)) return
    appCoroutineScope.launch {
      val rects = try {
        val quranInfo = quranInfoProvider()
        val bounds = quranInfo.getPageBounds(key.page)
        val startId = quranInfo.getAyahId(bounds[0], bounds[1])
        val endId = quranInfo.getAyahId(bounds[2], bounds[3])
        val occurrences = database.occurrencesInAyahRange(startId, endId)
        val rects = glyphBoundsSource.rectsForPage(key.page, key.width, occurrences)
        Log.i(
          TAG,
          "page ${key.page} width ${key.width}: ${occurrences.size} occurrences, ${rects.size} rects, " +
            "glyph db present=${glyphBoundsSource.hasDatabase(key.width)}"
        )
        rects
      } catch (e: Exception) {
        Log.w(TAG, "failed to load divine name bounds for page ${key.page}", e)
        emptyList()
      }
      cache[key] = rects
      loading.remove(key)
      if (rects.isNotEmpty()) {
        image.postInvalidate()
      }
    }
  }

  companion object {
    private const val TAG = "DivineNames"
    const val SUPPORTED_PAGE_TYPE = "madani"

    /** Translucent amber gold for the names of Allah; legible over the page in day and night modes. */
    const val NAME_COLOR = 0x80E0A21B.toInt()

    /** Translucent lemon yellow for the attributes of Allah. */
    const val ATTRIBUTE_COLOR = 0x99FFF24D.toInt()

    private const val CORNER_RADIUS_PX = 6f
  }
}
