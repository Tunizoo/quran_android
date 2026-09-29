package com.quran.mobile.feature.divinenames.model

data class DivineName(
  val id: String,
  val sortOrder: Int,
  val arabic: String,
  val transliteration: String,
  /** Explanation of the name following the methodology of the Salaf (affirmation without takyif or tamthil). */
  val meaning: String,
  /** Quranic (or prophetic) evidence text; may be empty for names established only in the Sunnah. */
  val evidence: String,
  val evidenceRef: String,
  val note: String,
  val sources: String,
  val isInQuran: Boolean,
  /** "name" for a name of Allah, "attribute" for one of His attributes (sifat). */
  val kind: String
) {
  val isAttribute: Boolean get() = kind == KIND_ATTRIBUTE

  companion object {
    const val KIND_NAME = "name"
    const val KIND_ATTRIBUTE = "attribute"
  }
}

/** A single word of the mushaf that is one of the divine names. [position] is the glyph position in the ayah. */
data class NameOccurrence(
  val ayahId: Int,
  val sura: Int,
  val ayah: Int,
  val position: Int,
  val nameId: String,
  /** The word as written in the uthmani text, e.g. "ٱلرَّحۡمَٰنِ". */
  val form: String,
  val kind: String
) {
  val isAttribute: Boolean get() = kind == DivineName.KIND_ATTRIBUTE
}

/** A highlighted occurrence with its rectangle in page image pixels. */
data class OccurrenceRect(val occurrence: NameOccurrence, val bounds: android.graphics.RectF)

data class NameInSelection(val name: DivineName, val occurrences: List<NameOccurrence>)
