package com.quran.mobile.feature.tajweed.model

import com.quran.mobile.feature.tajweed.R

/**
 * The colour groups of the well known "Tajweed Quran" (Dar Al-Maarifa) legend. Several rule
 * classes of the source data map to the same group, e.g. every place of ghunnah is green.
 */
enum class TajweedColorGroup(val dayColor: Int, val nightColor: Int, val labelResId: Int) {
  /** مد لازم (6 counts): dark red. */
  MADD_NECESSARY(0xFF9C0D0D.toInt(), 0xFFFF6B6B.toInt(), R.string.tajweed_legend_madd_necessary),

  /** مد واجب متصل (4-5 counts): red. */
  MADD_OBLIGATORY(0xFFE3200A.toInt(), 0xFFFF8A65.toInt(), R.string.tajweed_legend_madd_obligatory),

  /** مد جائز (2, 4 or 6 counts): orange. */
  MADD_PERMISSIBLE(0xFFF28C00.toInt(), 0xFFFFB74D.toInt(), R.string.tajweed_legend_madd_permissible),

  /** الغنة ومواضعها (ikhfa, idgham with ghunnah, iqlab, meem/noon mushaddad): green. */
  GHUNNAH(0xFF1E8E3E.toInt(), 0xFF69DB7C.toInt(), R.string.tajweed_legend_ghunnah),

  /** القلقلة: blue. */
  QALQALAH(0xFF1A73E8.toInt(), 0xFF74C0FC.toInt(), R.string.tajweed_legend_qalqalah),

  /** حروف لا تُلفظ (hamzat wasl, lam shamsiyah, silent letters, idgham without ghunnah): grey. */
  SILENT(0xFF9E9E9E.toInt(), 0xFF8A8A8A.toInt(), R.string.tajweed_legend_silent);

  fun color(night: Boolean): Int = if (night) nightColor else dayColor

  companion object {
    /**
     * Maps a quran.com rule class to its colour group, or null for rules that the printed mushaf
     * leaves in the base colour (the natural madd of two counts).
     */
    fun forRule(rule: String): TajweedColorGroup? = when (rule) {
      "madda_necessary" -> MADD_NECESSARY
      "madda_obligatory" -> MADD_OBLIGATORY
      "madda_permissible" -> MADD_PERMISSIBLE
      "ghunnah", "ikhafa", "ikhafa_shafawi", "idgham_ghunnah", "idgham_shafawi", "iqlab" -> GHUNNAH
      "qalaqah" -> QALQALAH
      "ham_wasl", "slnt", "laam_shamsiyah", "idgham_wo_ghunnah",
      "idgham_mutajanisayn", "idgham_mutaqaribayn" -> SILENT
      else -> null
    }
  }
}

/** A coloured range of a word: [start, end) in UTF-16 offsets of [TajweedWord.text]. */
data class TajweedSpan(val start: Int, val end: Int, val group: TajweedColorGroup)

/** A word of the mushaf with its tajweed colouring, keyed by its glyph position in the ayah. */
data class TajweedWord(
  val sura: Int,
  val ayah: Int,
  val position: Int,
  val text: String,
  val spans: List<TajweedSpan>
)
