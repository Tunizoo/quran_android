package com.quran.mobile.feature.memorization.session

import android.widget.ImageView
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.common.glyphbounds.GlyphBoundsSource
import com.quran.mobile.common.glyphbounds.GlyphPosition
import com.quran.mobile.common.glyphbounds.GlyphRect
import com.quran.mobile.feature.memorization.model.AyahRange
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Collections
import java.util.WeakHashMap

/**
 * Where the reveal cursor stands inside the portion: every word up to ([ayahId], [position]) is
 * visible, every word after it is hidden. [position] is the glyph position within the ayah
 * (0 = nothing of that ayah revealed yet).
 */
data class RevealCursor(val ayahId: Int, val position: Int) : Comparable<RevealCursor> {
  override fun compareTo(other: RevealCursor): Int =
    if (ayahId != other.ayahId) ayahId.compareTo(other.ayahId) else position.compareTo(other.position)
}

data class MemorizationSessionState(
  /** The portion being memorised; null when no session is running. */
  val range: AyahRange? = null,
  val startAyahId: Int = 0,
  val endAyahId: Int = 0,
  /** The spaced-repetition item being reviewed, when the session came from the schedule. */
  val itemId: Long? = null,
  /** false while the user listens or reads with everything visible. */
  val hidden: Boolean = true,
  /** Keep the first word of every ayah visible as a hint. */
  val hint: Boolean = true,
  val cursor: RevealCursor = RevealCursor(0, 0),
  /** Ayahs (global ayah ids) revealed by tapping one of their words. */
  val peekedAyahs: Set<Int> = emptySet(),
  /** Set once the cursor has passed the last word of the portion. */
  val completed: Boolean = false
) {
  val isActive: Boolean get() = range != null
}

/**
 * The running memorization session: which portion is hidden on the page and how much of it has
 * been revealed. Shared by the reader overlay (controls), the draw helper (painting) and the tap
 * handler (revealing a tapped ayah).
 */
@SingleIn(AppScope::class)
class MemorizationSession @Inject constructor(
  private val quranInfoProvider: () -> QuranInfo,
  private val glyphBoundsSource: GlyphBoundsSource
) {
  private val _state = MutableStateFlow(MemorizationSessionState())
  val state: StateFlow<MemorizationSessionState> = _state

  /** Width of the page images last drawn, which selects the glyph database. */
  @Volatile
  var pageImageWidth: Int = 0

  /** Page image views to redraw when the visible words change; held weakly. */
  private val pageViews: MutableSet<ImageView> = Collections.newSetFromMap(WeakHashMap())

  fun registerPageView(view: ImageView) {
    synchronized(pageViews) { pageViews.add(view) }
  }

  private fun notifyChanged() {
    val views = synchronized(pageViews) { pageViews.toList() }
    views.forEach { it.postInvalidate() }
  }

  fun start(range: AyahRange, itemId: Long?, hidden: Boolean = true) {
    val quranInfo = quranInfoProvider()
    val startId = quranInfo.getAyahId(range.start.sura, range.start.ayah)
    val endId = quranInfo.getAyahId(range.end.sura, range.end.ayah)
    _state.value = MemorizationSessionState(
      range = range,
      startAyahId = minOf(startId, endId),
      endAyahId = maxOf(startId, endId),
      itemId = itemId,
      hidden = hidden,
      hint = _state.value.hint,
      cursor = RevealCursor(minOf(startId, endId), 0)
    )
    notifyChanged()
  }

  fun stop() {
    _state.value = MemorizationSessionState(hint = _state.value.hint)
    notifyChanged()
  }

  /** Links the running session to a saved portion so it can be graded. */
  fun attachItem(itemId: Long) {
    _state.update { it.copy(itemId = itemId) }
  }

  fun setHidden(hidden: Boolean) {
    _state.update { it.copy(hidden = hidden) }
    notifyChanged()
  }

  fun setHint(hint: Boolean) {
    _state.update { it.copy(hint = hint) }
    notifyChanged()
  }

  /** Hides the whole portion again. */
  fun hideAll() {
    _state.update {
      it.copy(hidden = true, cursor = RevealCursor(it.startAyahId, 0), peekedAyahs = emptySet(), completed = false)
    }
    notifyChanged()
  }

  fun revealAll() {
    _state.update { it.copy(cursor = RevealCursor(it.endAyahId, Int.MAX_VALUE), completed = true) }
    notifyChanged()
  }

  /** Reveals the next hidden ayah of the portion. */
  fun revealNextAyah() {
    val state = _state.value
    if (!state.isActive || state.completed) return
    val cursor = state.cursor
    var target = if (cursor.position == Int.MAX_VALUE) cursor.ayahId + 1 else cursor.ayahId
    // ayahs already revealed by a tap do not need another press
    while (target < state.endAyahId && target in state.peekedAyahs) target++
    target = minOf(target, state.endAyahId)
    _state.update { it.copy(cursor = RevealCursor(target, Int.MAX_VALUE), completed = target >= it.endAyahId) }
    notifyChanged()
  }

  /** Reveals the whole ayah of the tapped word without moving the cursor. */
  fun peekAyah(position: GlyphPosition) {
    val ayahId = quranInfoProvider().getAyahId(position.sura, position.ayah)
    _state.update { it.copy(peekedAyahs = it.peekedAyahs + ayahId) }
    notifyChanged()
  }

  /** Whether the glyph at [position] is covered in [state]. */
  fun isHidden(state: MemorizationSessionState, position: GlyphPosition, isFirstWord: Boolean): Boolean {
    if (!state.isActive || !state.hidden) return false
    val ayahId = quranInfoProvider().getAyahId(position.sura, position.ayah)
    if (ayahId < state.startAyahId || ayahId > state.endAyahId) return false
    if (state.hint && isFirstWord) return false
    if (ayahId in state.peekedAyahs) return false
    return RevealCursor(ayahId, position.position) > state.cursor
  }

  /**
   * The word glyphs of an ayah (pause marks and the ayah marker excluded) in position order,
   * gathered from the page the ayah starts on and its neighbours (an ayah may span two pages).
   */
  fun wordsOfAyah(ayahId: Int): List<GlyphRect> {
    val width = pageImageWidth
    if (width <= 0) return emptyList()
    val quranInfo = quranInfoProvider()
    val suraAyah = quranInfo.getSuraAyahFromAyahId(ayahId)
    val page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
    val glyphs = ArrayList<GlyphRect>()
    for (p in (page - 1)..(page + 1)) {
      if (p < 1 || p > quranInfo.numberOfPages) continue
      glyphs.addAll(
        glyphBoundsSource.glyphsForPage(p, width).filter {
          it.position.sura == suraAyah.sura && it.position.ayah == suraAyah.ayah
        }
      )
    }
    if (glyphs.isEmpty()) return emptyList()
    val marker = glyphs.maxBy { it.position.position }
    return glyphs.filter { it !== marker && !it.isSmallMark }.sortedBy { it.position.position }
  }

  /** Default portion for a page: all the ayahs of that page. */
  fun pageRange(page: Int): AyahRange {
    val bounds = quranInfoProvider().getPageBounds(page)
    return AyahRange(SuraAyah(bounds[0], bounds[1]), SuraAyah(bounds[2], bounds[3]))
  }
}
