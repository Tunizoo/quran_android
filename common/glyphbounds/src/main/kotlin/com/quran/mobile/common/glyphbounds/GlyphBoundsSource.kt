package com.quran.mobile.common.glyphbounds

import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

/** A glyph of the mushaf identified by its ayah and its 1-based position in that ayah. */
data class GlyphPosition(val sura: Int, val ayah: Int, val position: Int)

/** A glyph with its rectangle in page image pixels and the line it sits on. */
data class GlyphRect(val position: GlyphPosition, val bounds: RectF, val line: Int) {
  /** Pause marks and similar small signs; everything else is a word, an ayah marker or an ornament. */
  val isSmallMark: Boolean get() = bounds.width() < 20f || bounds.height() < 40f
}

/**
 * Reads glyph rectangles from the `ayahinfo_<width>.db` database that ships with the page images.
 * [width] is the pixel width of the page image currently displayed, which is also the suffix of the
 * ayahinfo database describing it. Databases are opened lazily and kept open.
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
      // (a file, despite the name); its sibling files cover the other widths.
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

  /** Every glyph of [page], ordered by ayah and position. Empty when the database is missing. */
  fun glyphsForPage(page: Int, width: Int): List<GlyphRect> {
    val db = database(width) ?: return emptyList()
    val result = ArrayList<GlyphRect>()
    db.rawQuery(
      "SELECT sura_number, ayah_number, position, min_x, min_y, max_x, max_y, line_number " +
        "FROM glyphs WHERE page_number = ? ORDER BY sura_number, ayah_number, position",
      arrayOf(page.toString())
    ).use { cursor ->
      while (cursor.moveToNext()) {
        result.add(
          GlyphRect(
            GlyphPosition(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2)),
            RectF(
              cursor.getInt(3).toFloat(),
              cursor.getInt(4).toFloat(),
              cursor.getInt(5).toFloat(),
              cursor.getInt(6).toFloat()
            ),
            cursor.getInt(7)
          )
        )
      }
    }
    return result
  }

  /** Rectangles of the requested glyphs on [page]. */
  fun rectsForPage(page: Int, width: Int, positions: Collection<GlyphPosition>): List<GlyphRect> {
    if (positions.isEmpty()) return emptyList()
    val wanted = positions.toHashSet()
    return glyphsForPage(page, width).filter { it.position in wanted }
  }
}
