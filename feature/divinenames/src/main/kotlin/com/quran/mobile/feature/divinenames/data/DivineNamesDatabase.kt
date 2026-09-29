package com.quran.mobile.feature.divinenames.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.divinenames.model.DivineName
import com.quran.mobile.feature.divinenames.model.NameInSelection
import com.quran.mobile.feature.divinenames.model.NameOccurrence
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Read-only access to the bundled `divine_names.db` asset (names, explanations and the glyph
 * positions of every occurrence in the madani mushaf). The asset is copied to the app's database
 * directory on first use and re-copied whenever the bundled version changes.
 */
@SingleIn(AppScope::class)
class DivineNamesDatabase @Inject constructor(
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

  suspend fun allNames(): List<DivineName> = withContext(Dispatchers.IO) {
    val db = open() ?: return@withContext emptyList()
    db.rawQuery("SELECT $NAME_COLUMNS FROM names ORDER BY sort_order", null).use { cursor ->
      buildList { while (cursor.moveToNext()) add(cursor.toName()) }
    }
  }

  suspend fun name(id: String): DivineName? = withContext(Dispatchers.IO) {
    val db = open() ?: return@withContext null
    db.rawQuery("SELECT $NAME_COLUMNS FROM names WHERE id = ?", arrayOf(id)).use { cursor ->
      if (cursor.moveToFirst()) cursor.toName() else null
    }
  }

  /** Occurrences whose global ayah id lies in [startAyahId, endAyahId], ordered by ayah then position. */
  suspend fun occurrencesInAyahRange(startAyahId: Int, endAyahId: Int): List<NameOccurrence> =
    withContext(Dispatchers.IO) {
      val db = open() ?: return@withContext emptyList()
      db.rawQuery(
        "SELECT o.ayah_id, o.sura, o.ayah, o.position, o.name_id, o.form, n.kind " +
          "FROM occurrences o JOIN names n ON n.id = o.name_id " +
          "WHERE o.ayah_id >= ? AND o.ayah_id <= ? ORDER BY o.ayah_id, o.position",
        arrayOf(startAyahId.toString(), endAyahId.toString())
      ).use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(
              NameOccurrence(
                ayahId = cursor.getInt(0),
                sura = cursor.getInt(1),
                ayah = cursor.getInt(2),
                position = cursor.getInt(3),
                nameId = cursor.getString(4),
                form = cursor.getString(5),
                kind = cursor.getString(6)
              )
            )
          }
        }
      }
    }

  /** Names appearing in the ayah range, in order of first appearance, with their occurrences. */
  suspend fun namesInAyahRange(startAyahId: Int, endAyahId: Int): List<NameInSelection> {
    val occurrences = occurrencesInAyahRange(startAyahId, endAyahId)
    if (occurrences.isEmpty()) return emptyList()
    val grouped = occurrences.groupBy { it.nameId }
    val names = allNames().associateBy { it.id }
    return occurrences.map { it.nameId }.distinct().mapNotNull { id ->
      names[id]?.let { NameInSelection(it, grouped[id].orEmpty()) }
    }
  }

  private fun android.database.Cursor.toName(): DivineName = DivineName(
    id = getString(0),
    sortOrder = getInt(1),
    arabic = getString(2),
    transliteration = getString(3),
    meaning = getString(4),
    evidence = getString(5),
    evidenceRef = getString(6),
    note = getString(7),
    sources = getString(8),
    isInQuran = getInt(9) != 0,
    kind = getString(10)
  )

  companion object {
    const val DATABASE_NAME = "divine_names.db"

    /** Bump whenever the bundled asset changes so existing installs pick up the new copy. */
    const val BUNDLED_VERSION = "2"

    private const val NAME_COLUMNS =
      "id, sort_order, arabic, transliteration, meaning, evidence, evidence_ref, note, sources, in_quran, kind"
  }
}
