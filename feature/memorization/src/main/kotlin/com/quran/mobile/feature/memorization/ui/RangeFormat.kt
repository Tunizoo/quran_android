package com.quran.mobile.feature.memorization.ui

import android.content.Context
import com.quran.mobile.feature.memorization.R
import com.quran.mobile.feature.memorization.model.AyahRange
import com.quran.page.common.data.QuranNaming

/** "Al-Baqarah 1–5", or "Al-Baqarah 285 – Al Imran 3" when the portion crosses a sura. */
fun AyahRange.format(context: Context, naming: QuranNaming): String {
  val startName = naming.getSuraName(context, start.sura, wantPrefix = false)
  return if (isSingleSura) {
    if (start.ayah == end.ayah) {
      "$startName ${start.ayah}"
    } else {
      context.getString(R.string.memo_range_format, startName, start.ayah, end.ayah)
    }
  } else {
    val endName = naming.getSuraName(context, end.sura, wantPrefix = false)
    context.getString(R.string.memo_range_format_cross, startName, start.ayah, endName, end.ayah)
  }
}
