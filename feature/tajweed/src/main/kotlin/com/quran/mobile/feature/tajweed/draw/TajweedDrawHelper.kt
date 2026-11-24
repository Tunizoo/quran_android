package com.quran.mobile.feature.tajweed.draw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.common.glyphbounds.GlyphBoundsSource
import com.quran.mobile.common.glyphbounds.GlyphPlacements
import com.quran.mobile.common.glyphbounds.GlyphPosition
import com.quran.mobile.common.glyphbounds.GlyphRect
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.tajweed.TajweedSettings
import com.quran.mobile.feature.tajweed.data.TajweedDatabase
import com.quran.mobile.feature.tajweed.model.TajweedWord
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import com.quran.page.common.draw.OrderedImageDrawHelper
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.TreeMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Repaints the words of the page with tajweed colours.
 *
 * The mushaf pages are bitmaps, so letters cannot be recoloured in place. Instead, for every word
 * whose rectangle is known from the page glyph database, the original glyph is covered with the
 * page background (only where its ink is, so ayah highlights drawn under it keep their shape) and
 * the word is drawn again with the KFGQPC Uthmanic font, with a colour span per tajweed rule. The
 * words that follow each other on a line are set together in the room of their rectangles, with
 * one letter size, so a word is not always exactly on its rectangle: where it is drawn is recorded
 * in [GlyphPlacements]. Ayah markers, pause marks and other ornaments are copied from the page
 * bitmap unchanged. The result for a page is rendered once into an overlay bitmap and cached.
 *
 * Only the madani page type has word positions, so the helper is inactive for other page types.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ImageDrawHelper>())
class TajweedDrawHelper @Inject constructor(
  @ApplicationContext private val context: Context,
  private val settings: TajweedSettings,
  private val database: TajweedDatabase,
  private val glyphBoundsSource: GlyphBoundsSource,
  private val placements: GlyphPlacements,
  private val quranInfoProvider: () -> QuranInfo,
  private val appCoroutineScope: AppCoroutineScope,
  @Named(DependencyInjectionConstants.CURRENT_PAGE_TYPE) private val pageType: String
) : OrderedImageDrawHelper {

  /** Negative: draws under the ayah highlights, and before the other helpers (e.g. divine names). */
  override val drawOrder: Int = -10

  private data class PageKey(val page: Int, val width: Int)

  /**
   * A run of words set together. [markRight] and [markLeft] tell whether a glyph left as drawn on
   * the page (rather than the end of the line) is on that side of the run.
   */
  private class Run(val markRight: Boolean, val markLeft: Boolean)

  /** [runs] is the run of each word: see [runsOf]. */
  private class PageData(val words: List<Pair<GlyphRect, TajweedWord>>, val runs: List<Run>)

  private data class OverlayKey(
    val page: Int,
    val width: Int,
    val matrix: List<Float>,
    val viewWidth: Int,
    val viewHeight: Int,
    val paddingLeft: Int,
    val paddingTop: Int,
    val generation: Int,
    val backgroundId: Int,
    val backgroundOffsetX: Int,
    val backgroundOffsetY: Int
  )

  private val pageCache: MutableMap<PageKey, PageData> = Collections.synchronizedMap(HashMap())
  private val loadingPages: MutableSet<PageKey> = Collections.synchronizedSet(HashSet())
  private val overlays = object : LinkedHashMap<OverlayKey, Bitmap>(4, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<OverlayKey, Bitmap>): Boolean {
      val evict = size > MAX_OVERLAYS
      if (evict) eldest.value.recycle()
      return evict
    }
  }
  private val buildingOverlays: MutableSet<OverlayKey> = Collections.synchronizedSet(HashSet())

  private val legend by lazy { TajweedLegend(context) }
  private val typeface: Typeface? by lazy {
    try {
      Typeface.createFromAsset(context.assets, FONT_ASSET)
    } catch (e: Exception) {
      Log.w(TAG, "font $FONT_ASSET not available; tajweed colouring disabled", e)
      null
    }
  }

  val isActive: Boolean get() = settings.isEnabled && pageType == SUPPORTED_PAGE_TYPE

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    if (!isActive) {
      placements.clear()
      return
    }
    val drawable = image.drawable ?: return
    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return
    val width = bitmap.width
    if (width <= 0 || typeface == null) return

    val pageKey = PageKey(pageCoordinates.page, width)
    val pageData = pageCache[pageKey]
    if (pageData == null) {
      loadPage(pageKey, image)
      return
    }

    val background = findBackground(image)
    val matrixValues = FloatArray(9).also { image.imageMatrix.getValues(it) }
    val key = OverlayKey(
      page = pageCoordinates.page,
      width = width,
      matrix = matrixValues.toList(),
      viewWidth = image.width,
      viewHeight = image.height,
      paddingLeft = image.paddingLeft,
      paddingTop = image.paddingTop,
      generation = settings.generation,
      backgroundId = background?.let { System.identityHashCode(it.drawable) } ?: 0,
      backgroundOffsetX = background?.offsetX ?: 0,
      backgroundOffsetY = background?.offsetY ?: 0
    )
    val overlay = synchronized(overlays) { overlays[key] }
    if (overlay == null) {
      buildOverlay(key, pageData, image, bitmap, background)
      return
    }
    canvas.drawBitmap(overlay, 0f, 0f, null)

    val pageBottom = RectF(0f, 0f, width.toFloat(), bitmap.height.toFloat()).also {
      image.imageMatrix.mapRect(it)
    }.bottom + image.paddingTop
    legend.draw(
      canvas,
      image.width.toFloat(),
      image.height.toFloat(),
      pageBottom,
      settings.isNightMode,
      settings.textColor
    )
  }

  // ------------------------------------------------------------------------------------ page data

  private fun loadPage(key: PageKey, image: ImageView) {
    if (!loadingPages.add(key)) return
    appCoroutineScope.launch {
      val data = try {
        val quranInfo = quranInfoProvider()
        val bounds = quranInfo.getPageBounds(key.page)
        val words = database.wordsInRange(bounds[0], bounds[1], bounds[2], bounds[3])
        val byPosition = words.associateBy { Triple(it.sura, it.ayah, it.position) }
        val glyphs = glyphBoundsSource.glyphsForPage(key.page, key.width)
        val wordGlyphs = ArrayList<Pair<GlyphRect, TajweedWord>>()
        val markers = ArrayList<GlyphRect>()
        for (glyph in glyphs) {
          val word = byPosition[Triple(glyph.position.sura, glyph.position.ayah, glyph.position.position)]
          if (word != null && !glyph.isSmallMark) wordGlyphs.add(glyph to word) else markers.add(glyph)
        }
        Log.i(TAG, "page ${key.page} width ${key.width}: ${wordGlyphs.size} words, ${markers.size} other glyphs")
        PageData(wordGlyphs, runsOf(wordGlyphs, markers))
      } catch (e: Exception) {
        Log.w(TAG, "failed to load tajweed data for page ${key.page}", e)
        PageData(emptyList(), emptyList())
      }
      pageCache[key] = data
      loadingPages.remove(key)
      if (data.words.isNotEmpty()) image.postInvalidate()
    }
  }

  /**
   * Words that follow each other on a line, with nothing left from the page image between them, are
   * set together as one run (see [layoutRuns]). The glyphs that stay as drawn on the page (ayah
   * markers, pause marks) end a run, so every word stays on its side of them. Returns the run of
   * each word.
   */
  private fun runsOf(words: List<Pair<GlyphRect, TajweedWord>>, markers: List<GlyphRect>): List<Run> {
    class Item(val centerX: Float, val word: Int)
    val lines = TreeMap<Int, MutableList<Item>>()
    words.forEachIndexed { index, (glyph, _) ->
      lines.getOrPut(glyph.line) { ArrayList() }.add(Item(glyph.bounds.centerX(), index))
    }
    for (marker in markers) {
      lines.getOrPut(marker.line) { ArrayList() }.add(Item(marker.bounds.centerX(), -1))
    }
    val runs = arrayOfNulls<Run>(words.size)
    for (items in lines.values) {
      items.sortByDescending { it.centerX } // right to left
      val pending = ArrayList<Int>()
      var markRight = false
      for (item in items) {
        if (item.word >= 0) {
          pending.add(item.word)
        } else {
          val run = Run(markRight, markLeft = true)
          for (word in pending) runs[word] = run
          pending.clear()
          markRight = true
        }
      }
      val run = Run(markRight, markLeft = false)
      for (word in pending) runs[word] = run
    }
    return runs.map { it ?: Run(markRight = false, markLeft = false) }
  }

  // ------------------------------------------------------------------------------------- overlay

  private class Background(val drawable: Drawable, val offsetX: Int, val offsetY: Int)

  /** The nearest ancestor background and the offset of the image view inside that ancestor. */
  private fun findBackground(image: ImageView): Background? {
    var offsetX = 0
    var offsetY = 0
    var view: View = image
    while (true) {
      val parent = view.parent as? View ?: return null
      offsetX += view.left - parent.scrollX
      offsetY += view.top - parent.scrollY
      val background = parent.background
      if (background != null) return Background(background, offsetX, offsetY)
      view = parent
    }
  }

  private fun buildOverlay(key: OverlayKey, pageData: PageData, image: ImageView, pageBitmap: Bitmap, background: Background?) {
    if (!buildingOverlays.add(key)) return
    val matrix = Matrix().apply { setValues(key.matrix.toFloatArray()) }
    val imageColorFilter = image.colorFilter
    val textColor = settings.textColor
    val night = settings.isNightMode
    val fallbackBackground = settings.fallbackBackgroundColor
    appCoroutineScope.launch(Dispatchers.Default) {
      val overlay = try {
        render(key, pageData, matrix, pageBitmap, imageColorFilter, textColor, night, background, fallbackBackground)
      } catch (e: Exception) {
        Log.w(TAG, "failed to render tajweed overlay for page ${key.page}", e)
        null
      }
      if (overlay != null) {
        synchronized(overlays) { overlays[key] = overlay }
      }
      buildingOverlays.remove(key)
      if (overlay != null) image.postInvalidate()
    }
  }

  private fun render(
    key: OverlayKey,
    pageData: PageData,
    matrix: Matrix,
    pageBitmap: Bitmap,
    imageColorFilter: ColorFilter?,
    textColor: Int,
    night: Boolean,
    background: Background?,
    fallbackBackground: Int
  ): Bitmap {
    val overlay = Bitmap.createBitmap(max(1, key.viewWidth), max(1, key.viewHeight), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(overlay)
    val padX = key.paddingLeft.toFloat()
    val padY = key.paddingTop.toFloat()
    val viewRect = RectF()

    // 1. cover the ink of every word with the page background: the page's alpha channel is the ink
    //    mask, painted with the background colour (or a shader of the background drawable)
    val inkMask = pageBitmap.extractAlpha()
    val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val colorBackground = background?.drawable as? ColorDrawable
    if (background == null) {
      coverPaint.color = fallbackBackground
    } else if (colorBackground != null) {
      coverPaint.color = colorBackground.color
    } else {
      val bounds = background.drawable.bounds
      val backdrop = Bitmap.createBitmap(max(1, bounds.width()), max(1, bounds.height()), Bitmap.Config.ARGB_8888)
      Canvas(backdrop).also { c ->
        c.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
        background.drawable.draw(c)
      }
      coverPaint.shader = BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply {
          setTranslate((bounds.left - background.offsetX).toFloat(), (bounds.top - background.offsetY).toFloat())
        })
      }
    }
    val src = Rect()
    for ((glyph, _) in pageData.words) {
      glyph.bounds.round(src)
      src.inset(-1, -1)
      src.intersect(0, 0, inkMask.width, inkMask.height)
      matrix.mapRect(viewRect, RectF(src))
      viewRect.offset(padX, padY)
      canvas.drawBitmap(inkMask, src, viewRect, coverPaint)
    }
    inkMask.recycle()

    // 2. words, drawn with the Uthmanic font and tajweed colours
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      typeface = this@TajweedDrawHelper.typeface
      textSize = BASE_TEXT_SIZE
      color = textColor
      style = Paint.Style.FILL_AND_STROKE
      strokeWidth = STROKE_WIDTH
      strokeJoin = Paint.Join.ROUND
    }
    val measured = ArrayList<MeasuredWord>(pageData.words.size)
    val targets = drawTargets(pageData.words)
    pageData.words.forEachIndexed { index, (glyph, word) ->
      val target = RectF()
      matrix.mapRect(target, targets[index])
      target.offset(padX, padY)
      measure(word, glyph.position, target, pageData.runs[index], textPaint, night)?.let { measured.add(it) }
    }
    if (measured.isNotEmpty()) {
      val pageScale = layoutRuns(measured)
      for (item in measured) drawMeasured(canvas, item, pageScale, textPaint)

      // where the words are now, in page image pixels, for the helpers that draw on words
      val toPage = Matrix()
      if (matrix.invert(toPage)) {
        val placed = HashMap<GlyphPosition, RectF>(measured.size * 2)
        for (item in measured) {
          val rect = RectF(item.left, item.target.top, item.left + item.inkBounds.width() * item.scaleX, item.target.bottom)
          rect.offset(-padX, -padY)
          toPage.mapRect(rect)
          placed[item.position] = rect
        }
        placements.set(key.page, key.width, placed)
      }
    }
    return overlay
  }

  /**
   * The calligrapher justifies the lines by lengthening words (kashida), so the boxes of the words
   * are not in proportion to their width in the text font. Stretching every word into its own box
   * gives letters of a different size from one word to the next. Instead the words of a run are set
   * together: one stretch for the whole run, kept within narrow limits, and the room that is left
   * goes between the words and next to the ayah marker or pause mark at an end of the run (never at
   * the end of the line). Sets where each word is drawn and returns the scale of the page font.
   */
  private fun layoutRuns(measured: List<MeasuredWord>): Float {
    class Group(val run: Run, val words: List<MeasuredWord>) {
      val box = words.sumOf { it.target.width().toDouble() }.toFloat()
      val ink = words.sumOf { it.inkBounds.width().toDouble() }.toFloat()
      val ratio = box / ink
    }
    val runs = measured.groupBy { it.run }.map { (run, words) -> Group(run, words.sortedByDescending { it.target.right }) }

    // one font size for the page: the box/text ratio below which lies FONT_QUANTILE of the text.
    // The denser runs are squeezed, the others keep the page size and get more room between words.
    val sorted = runs.sortedBy { it.ratio }
    val total = sorted.sumOf { it.ink.toDouble() }
    var seen = 0.0
    var pageScale = sorted.first().ratio
    for (run in sorted) {
      seen += run.ink
      pageScale = run.ratio
      if (seen >= total * FONT_QUANTILE) break
    }

    for (run in runs) {
      val count = run.words.size
      var scale = (run.ratio / pageScale).coerceIn(MIN_STRETCH, MAX_STRETCH) * pageScale
      // a run of few words in a wide space: beyond some room between the words, stretch them after all
      val slots = count - 1 + (if (run.run.markRight) 1 else 0) + (if (run.run.markLeft) 1 else 0)
      val room = MAX_EXTRA_GAP * BASE_TEXT_SIZE * pageScale * max(slots, 1)
      if (run.box - scale * run.ink > room) {
        scale = min((run.box - room) / run.ink, LAST_RESORT_STRETCH * pageScale)
      }
      val slack = run.box - scale * run.ink
      var extra = 0f
      var right = run.words[0].target.right
      if (slack >= 0f && slots > 0) {
        extra = slack / slots
        if (run.run.markRight) right -= extra
      } else if (slack < 0f && count > 1) {
        extra = slack / (count - 1) // too dense even squeezed: the words close up
      } else {
        right -= slack / 2f
      }
      run.words.forEachIndexed { index, word ->
        word.scaleX = scale
        word.left = right - word.inkBounds.width() * scale
        if (index + 1 < count) {
          right = word.left - (word.target.left - run.words[index + 1].target.right) - extra
        }
      }
    }
    return pageScale
  }

  /**
   * Boxes the words are drawn into. In the mushaf the calligrapher sometimes lets a word reach over
   * its neighbour (the kaf of «قَبۡلِكَ» above «فَأَمۡلَيۡتُ» in 13:32), so their glyph boxes overlap;
   * drawn side by side with the text font the two words would cover each other. The overlap is split
   * in the middle. Words are in reading order: on one line the next word lies to the left.
   */
  private fun drawTargets(words: List<Pair<GlyphRect, TajweedWord>>): List<RectF> {
    val targets = words.map { RectF(it.first.bounds) }
    for (i in 1 until words.size) {
      if (words[i - 1].first.line != words[i].first.line) continue
      val previous = targets[i - 1]
      val current = targets[i]
      if (current.right - previous.left <= MIN_OVERLAP_PX) continue
      val middle = (current.right + previous.left) / 2f
      if (middle - current.left < MIN_TARGET_WIDTH_PX || previous.right - middle < MIN_TARGET_WIDTH_PX) continue
      previous.left = middle
      current.right = middle
    }
    return targets
  }

  /** Glyphs repainted over the word in another colour (a mark coloured on its own). */
  private class GlyphOverlay(val glyphIds: IntArray, val positions: FloatArray, val font: Font, val color: Int)

  private class MeasuredWord(
    val position: GlyphPosition,
    val layout: StaticLayout,
    val overlays: List<GlyphOverlay>,
    val inkBounds: Rect,
    /** The box of the word on the page, in view pixels. */
    val target: RectF,
    val run: Run
  ) {
    /** Set by [layoutRuns]: the left edge of the ink and its horizontal scale, in view pixels. */
    var left = 0f
    var scaleX = 1f
  }

  private fun isMark(ch: Char): Boolean {
    val type = Character.getType(ch)
    return type == Character.NON_SPACING_MARK.toInt() ||
      type == Character.COMBINING_SPACING_MARK.toInt() ||
      type == Character.ENCLOSING_MARK.toInt()
  }

  private fun measure(
    word: TajweedWord,
    position: GlyphPosition,
    target: RectF,
    run: Run,
    textPaint: TextPaint,
    night: Boolean
  ): MeasuredWord? {
    val text = word.text
    if (text.isEmpty()) return null

    // The word is always laid out from its complete text: this font picks letter forms by the marks
    // around them, so a word drawn without one of its marks is not the same word.
    //
    // A letter and its marks are one glyph cluster. A colour span that starts or ends inside a
    // cluster makes the text renderer shape the marks apart from their letter, which misplaces them
    // (the tanween landing on the dal of «لَشَدِيدٞ»). So the layout only gets one colour per
    // cluster, the colour of its letter, and a mark whose colour differs (the tanween of an idgham
    // on a letter pronounced normally; the plain tanween of a qalqalah letter) is repainted on top
    // as a glyph of its own (Android 12+). Where that is not possible the whole cluster takes one
    // colour: the rule's.
    val length = text.length
    val colors = IntArray(length) { NO_COLOR }
    for (span in word.spans) {
      val color = span.group.color(night)
      for (i in max(0, span.start) until min(length, span.end)) colors[i] = color
    }
    val shaper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MarkGlyphs.Shaper(text, textPaint) else null
    val overlays = ArrayList<GlyphOverlay>()
    var clusterStart = 0
    while (clusterStart < length) {
      var clusterEnd = clusterStart + 1
      while (clusterEnd < length && isMark(text[clusterEnd])) clusterEnd++
      var clusterColor = colors[clusterStart]
      var markStart = clusterStart + 1
      while (markStart < clusterEnd) {
        val markColor = colors[markStart]
        var markEnd = markStart + 1
        while (markEnd < clusterEnd && colors[markEnd] == markColor) markEnd++
        if (markColor != colors[clusterStart]) {
          val glyphs = shaper?.find(markStart, markEnd)
          if (glyphs != null) {
            val paintColor = if (markColor == NO_COLOR) textPaint.color else markColor
            overlays.add(GlyphOverlay(glyphs.glyphIds, glyphs.positions, glyphs.font, paintColor))
          } else if (clusterColor == NO_COLOR) {
            clusterColor = markColor
          }
        }
        markStart = markEnd
      }
      for (i in clusterStart until clusterEnd) colors[i] = clusterColor
      clusterStart = clusterEnd
    }

    val spannable = SpannableString(text)
    var runStart = 0
    while (runStart < length) {
      var runEnd = runStart + 1
      while (runEnd < length && colors[runEnd] == colors[runStart]) runEnd++
      if (colors[runStart] != NO_COLOR) {
        spannable.setSpan(ForegroundColorSpan(colors[runStart]), runStart, runEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
      runStart = runEnd
    }

    val inkBounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, inkBounds)
    if (inkBounds.width() <= 0 || inkBounds.height() <= 0) return null
    val advance = ceil(textPaint.measureText(text)).toInt() + 2
    val layout = StaticLayout.Builder.obtain(spannable, 0, spannable.length, textPaint, max(advance, inkBounds.right + 2))
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setTextDirection(TextDirectionHeuristics.RTL)
      .setIncludePad(false)
      .build()
    return MeasuredWord(position, layout, overlays, inkBounds, target, run)
  }

  private fun drawMeasured(canvas: Canvas, item: MeasuredWord, scaleY: Float, textPaint: TextPaint) {
    val layout = item.layout
    val inkBounds = item.inkBounds
    val target = item.target
    val lineLeft = layout.getLineLeft(0)
    val baseline = layout.getLineBaseline(0).toFloat()
    val scaleX = item.scaleX
    val translateX = item.left - scaleX * (lineLeft + inkBounds.left)
    val translateY = target.centerY() - scaleY * (baseline + (inkBounds.top + inkBounds.bottom) / 2f)
    canvas.save()
    canvas.translate(translateX, translateY)
    canvas.scale(scaleX, scaleY)
    layout.draw(canvas)
    if (item.overlays.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // glyph positions are relative to the left end of the line, on its baseline
      canvas.translate(lineLeft, baseline)
      for (overlay in item.overlays) {
        MarkGlyphs.draw(canvas, overlay.glyphIds, overlay.positions, overlay.font, overlay.color, textPaint)
      }
    }
    canvas.restore()
  }

  companion object {
    private const val TAG = "Tajweed"
    const val SUPPORTED_PAGE_TYPE = "madani"

    /** Bundled with the madani flavour of the app; the same typeface the reader uses for ayah text. */
    private const val FONT_ASSET = "uthmanic_hafs_ver12.otf"

    private const val BASE_TEXT_SIZE = 100f

    /** Slight emboldening (at [BASE_TEXT_SIZE]) so the text font matches the weight of the page glyphs. */
    private const val STROKE_WIDTH = 2.8f
    /** Marks a character without a tajweed colour (fully transparent is never a rule colour). */
    private const val NO_COLOR = 0

    /** Share of the text of a page that is denser than the page font size and gets squeezed. */
    private const val FONT_QUANTILE = 0.25f
    /** Horizontal stretch of a run of words relative to the page font size. */
    private const val MIN_STRETCH = 0.6f
    private const val MAX_STRETCH = 1.06f
    /** Room (in em) added between two words before the run is stretched beyond [MAX_STRETCH]. */
    private const val MAX_EXTRA_GAP = 0.3f
    private const val LAST_RESORT_STRETCH = 1.45f
    /** Overlap of two neighbouring word boxes (page image pixels) that gets split. */
    private const val MIN_OVERLAP_PX = 4f
    private const val MIN_TARGET_WIDTH_PX = 12f
    private const val MAX_OVERLAYS = 3
  }
}
