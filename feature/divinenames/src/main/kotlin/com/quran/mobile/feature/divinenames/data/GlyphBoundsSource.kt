package com.quran.mobile.feature.divinenames.data

import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.mobile.feature.divinenames.model.NameOccurrence
import com.quran.mobile.feature.divinenames.model.OccurrenceRect
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

/**
 * Reads word glyph rectangles for a page from the `ayahinfo_<width>.db` database that ships with
 * the page images. [width] is the pixel width of the page image currently displayed, which is also
 * the suffix of the ayahinfo database that describes it.
 */
@SingleIn(AppScope::class)
class GlyphBoundsSource @Inject constructor(
  private val quranFileManager: QuranFileManager
) {
  private val lock = Any()
  private val databases = HashMap<Int, SQLiteDatabase?>()

  private fun database(width: Int): SQLiteDatabase? {
    synchronized(lock) {
      if (databases.containsKey(width)) return databases[width]
      // ayahInfoFileDirectory() returns the path of the ayahinfo database for the current width
      // parameter (a file, despite the name); its sibling files cover the other widths.
      val current = quranFileManager.ayahInfoFileDirectory()
      val directory = if (current.isDirectory) current else current.parentFile
      val file = File(directory, "ayahinfo_$width.db")
      val db = if (file.exists()) {
        try {
          SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
          null
        }
      } else {
        null
      }
      databases[width] = db
      return db
    }
  }

  fun hasDatabase(width: Int): Boolean = database(width) != null

  /** Rectangles (in page image pixels) of the given occurrences on [page]. */
  fun rectsForPage(page: Int, width: Int, occurrences: List<NameOccurrence>): List<OccurrenceRect> {
    if (occurrences.isEmpty()) return emptyList()
    val db = database(width) ?: return emptyList()
    val wanted = occurrences.associateBy { Triple(it.sura, it.ayah, it.position) }
    val rects = ArrayList<OccurrenceRect>(wanted.size)
    db.rawQuery(
      "SELECT sura_number, ayah_number, position, min_x, min_y, max_x, max_y FROM glyphs WHERE page_number = ?",
      arrayOf(page.toString())
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val key = Triple(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2))
        val occurrence = wanted[key]
        if (occurrence != null) {
          rects.add(
            OccurrenceRect(
              occurrence,
              RectF(
                cursor.getInt(3).toFloat(),
                cursor.getInt(4).toFloat(),
                cursor.getInt(5).toFloat(),
                cursor.getInt(6).toFloat()
              )
            )
          )
        }
      }
    }
    return rects
  }
}
