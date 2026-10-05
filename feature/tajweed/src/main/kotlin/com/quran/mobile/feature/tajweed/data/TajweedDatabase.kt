package com.quran.mobile.feature.tajweed.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.tajweed.model.TajweedColorGroup
import com.quran.mobile.feature.tajweed.model.TajweedSpan
import com.quran.mobile.feature.tajweed.model.TajweedWord
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Read-only access to the bundled `tajweed.db` asset: the text of every word of the madani mushaf
 * with its tajweed spans, keyed by glyph position. The asset is copied to the app's database
 * directory on first use and re-copied whenever the bundled version changes.
 */
@SingleIn(AppScope::class)
class TajweedDatabase @Inject constructor(
  @ApplicationContext private val context: Context
) {
  private val lock = Any()
  private var database: SQLiteDatabase? = null

  private fun open(): SQLiteDatabase? {
    synchronized(lock) {
      database?.let { return it }
      return try {
        val file = ensureCopied()
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).also {
          database = it
        }
      } catch (e: Exception) {
        null
      }
    }
  }

  private fun ensureCopied(): File {
    val target = context.getDatabasePath(DATABASE_NAME)
    val versionFile = File(target.parentFile, "$DATABASE_NAME.version")
    val installedVersion = if (versionFile.exists()) versionFile.readText().trim() else ""
    if (!target.exists() || installedVersion != BUNDLED_VERSION) {
      target.parentFile?.mkdirs()
      context.assets.open(DATABASE_NAME).use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
      }
      versionFile.writeText(BUNDLED_VERSION)
    }
    return target
  }

  /** Words of the ayahs from (startSura, startAyah) to (endSura, endAyah), both inclusive. */
  suspend fun wordsInRange(startSura: Int, startAyah: Int, endSura: Int, endAyah: Int): List<TajweedWord> =
    withContext(Dispatchers.IO) {
      val db = open() ?: return@withContext emptyList()
      db.rawQuery(
        "SELECT sura, ayah, position, text, spans FROM words " +
          "WHERE (sura > ? OR (sura = ? AND ayah >= ?)) AND (sura < ? OR (sura = ? AND ayah <= ?)) " +
          "ORDER BY sura, ayah, position",
        arrayOf(
          startSura.toString(), startSura.toString(), startAyah.toString(),
          endSura.toString(), endSura.toString(), endAyah.toString()
        )
      ).use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            val text = cursor.getString(3)
            add(
              TajweedWord(
                sura = cursor.getInt(0),
                ayah = cursor.getInt(1),
                position = cursor.getInt(2),
                text = text,
                spans = parseSpans(cursor.getString(4), text.length)
              )
            )
          }
        }
      }
    }

  private fun parseSpans(encoded: String, length: Int): List<TajweedSpan> {
    if (encoded.isEmpty()) return emptyList()
    return encoded.split(';').mapNotNull { item ->
      val colon = item.indexOf(':')
      val dash = item.indexOf('-')
      if (colon < 0 || dash < 0 || dash > colon) return@mapNotNull null
      val start = item.substring(0, dash).toIntOrNull() ?: return@mapNotNull null
      val end = item.substring(dash + 1, colon).toIntOrNull() ?: return@mapNotNull null
      val group = TajweedColorGroup.forRule(item.substring(colon + 1)) ?: return@mapNotNull null
      if (start < 0 || end > length || start >= end) null else TajweedSpan(start, end, group)
    }
  }

  companion object {
    const val DATABASE_NAME = "tajweed.db"

    /** Bump whenever the bundled asset changes so existing installs pick up the new copy. */
    const val BUNDLED_VERSION = "5"
  }
}
