package com.quran.mobile.feature.memorization.draw

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.common.glyphbounds.GlyphBoundsSource
import com.quran.mobile.common.glyphbounds.GlyphPosition
import com.quran.mobile.common.glyphbounds.GlyphRect
import com.quran.mobile.feature.memorization.session.MemorizationSession
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import com.quran.page.common.draw.OrderedImageDrawHelper
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Covers the words of the portion being memorised with the page background, leaving a faint box
 * where each hidden word is so the reader can tap it to peek. Draws over the ayah highlights
 * (positive order) so covered words stay covered while audio plays.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ImageDrawHelper>())
class MemorizationDrawHelper @Inject constructor(
  private val session: MemorizationSession,
  private val colors: MemorizationPageColors,
  private val glyphBoundsSource: GlyphBoundsSource,
  private val quranInfoProvider: () -> QuranInfo,
  private val appCoroutineScope: AppCoroutineScope
) : OrderedImageDrawHelper {

  override val drawOrder: Int = 10

  private data class Key(val page: Int, val width: Int)

  /** A word of the page with whether it is the first word of its ayah. */
  private class PageWord(val glyph: GlyphRect, val isFirstWord: Boolean)

  private val cache: MutableMap<Key, List<PageWord>> = Collections.synchronizedMap(HashMap())
  private val loading: MutableSet<Key> = Collections.synchronizedSet(HashSet())

  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val scaledRect = RectF()

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    // register before the active check so that starting a session refreshes the visible pages
    session.registerPageView(image)
    val state = session.state.value
    if (!state.isActive) return
    val width = imageWidth(image.drawable) ?: return
    session.pageImageWidth = width

    val key = Key(pageCoordinates.page, width)
    val words = cache[key]
    if (words == null) {
      load(key, image)
      return
    }
    if (!state.hidden) return

    fillPaint.color = backgroundColor(image)
    val textColor = colors.textColor
    boxPaint.color = Color.argb(70, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
    boxPaint.strokeWidth = image.imageMatrix.mapRadius(2f)
    val radius = image.imageMatrix.mapRadius(CORNER_RADIUS_PX)
    val insetX = image.imageMatrix.mapRadius(COVER_PADDING_X_PX)
    val insetY = image.imageMatrix.mapRadius(COVER_PADDING_Y_PX)
    // cover first (diacritics of one word may reach into the box of its neighbour), then outline
    for (word in words) {
      if (!session.isHidden(state, word.glyph.position, word.isFirstWord)) continue
      image.imageMatrix.mapRect(scaledRect, word.glyph.bounds)
      scaledRect.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
      scaledRect.inset(-insetX, -insetY)
      canvas.drawRoundRect(scaledRect, radius, radius, fillPaint)
    }
    for (word in words) {
      if (word.glyph.isSmallMark || !session.isHidden(state, word.glyph.position, word.isFirstWord)) continue
      image.imageMatrix.mapRect(scaledRect, word.glyph.bounds)
      scaledRect.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
      scaledRect.inset(-insetX / 2, -insetY / 2)
      canvas.drawRoundRect(scaledRect, radius, radius, boxPaint)
    }
  }

  /** The hidden word under ([pageX], [pageY]) in page image pixels, or null. */
  fun hiddenWordAt(page: Int, image: ImageView, pageX: Float, pageY: Float): GlyphPosition? {
    val state = session.state.value
    if (!state.isActive || !state.hidden) return null
    val width = imageWidth(image.drawable) ?: return null
    val words = cache[Key(page, width)] ?: return null
    val hit = RectF()
    return words.firstOrNull {
      hit.set(it.glyph.bounds)
      hit.inset(-COVER_PADDING_X_PX, -COVER_PADDING_Y_PX)
      !it.glyph.isSmallMark && hit.contains(pageX, pageY) &&
        session.isHidden(state, it.glyph.position, it.isFirstWord)
    }?.glyph?.position
  }

  private fun imageWidth(drawable: Drawable?): Int? {
    if (drawable == null) return null
    val width = (drawable as? BitmapDrawable)?.bitmap?.width ?: drawable.intrinsicWidth
    return if (width > 0) width else null
  }

  /** The plain colour behind the page image, or the reader's default for the current mode. */
  private fun backgroundColor(image: ImageView): Int {
    var view: View = image
    while (true) {
      val parent = view.parent as? View ?: return colors.fallbackBackgroundColor
      val background = parent.background
      if (background is ColorDrawable) return background.color
      if (background != null) return colors.fallbackBackgroundColor
      view = parent
    }
  }

  private fun load(key: Key, image: ImageView) {
    if (!loading.add(key)) return
    appCoroutineScope.launch {
      val words = try {
        pageWords(key)
      } catch (e: Exception) {
        Log.w(TAG, "failed to load glyphs for page ${key.page}", e)
        emptyList()
      }
      cache[key] = words
      loading.remove(key)
      image.postInvalidate()
    }
  }

  /**
   * The word glyphs of a page. The ayah marker is the last glyph of an ayah and sits on the page
   * where the ayah ends (that page's bounds include the ayah). An ayah that continues from the
   * previous page has its first word there, not here.
   */
  private fun pageWords(key: Key): List<PageWord> {
    val glyphs = glyphBoundsSource.glyphsForPage(key.page, key.width)
    if (glyphs.isEmpty()) return emptyList()
    val quranInfo = quranInfoProvider()
    val bounds = quranInfo.getPageBounds(key.page)
    val firstId = quranInfo.getAyahId(bounds[0], bounds[1])
    val lastId = quranInfo.getAyahId(bounds[2], bounds[3])
    val previousPageAyahs: Set<Pair<Int, Int>> = if (key.page > 1) {
      glyphBoundsSource.glyphsForPage(key.page - 1, key.width)
        .filter { !it.isSmallMark }
        .map { it.position.sura to it.position.ayah }
        .toHashSet()
    } else {
      emptySet()
    }
    val byAyah = glyphs.groupBy { it.position.sura to it.position.ayah }
    val words = ArrayList<PageWord>(glyphs.size)
    for ((ayah, ayahGlyphs) in byAyah) {
      val ayahId = quranInfo.getAyahId(ayah.first, ayah.second)
      val endsHere = ayahId in firstId..lastId
      val marker = if (endsHere) ayahGlyphs.maxBy { it.position.position } else null
      val continues = ayah in previousPageAyahs
      // pause marks are covered with the words around them but never count as a first word
      val wordGlyphs = ayahGlyphs.filter { it !== marker }.sortedBy { it.position.position }
      var firstWordSeen = continues
      for (glyph in wordGlyphs) {
        val isFirst = !glyph.isSmallMark && !firstWordSeen
        if (isFirst) firstWordSeen = true
        words.add(PageWord(glyph, isFirstWord = isFirst))
      }
    }
    return words
  }

  companion object {
    private const val TAG = "Memorization"
    private const val CORNER_RADIUS_PX = 8f
    private const val COVER_PADDING_X_PX = 6f
    private const val COVER_PADDING_Y_PX = 16f
  }
}
