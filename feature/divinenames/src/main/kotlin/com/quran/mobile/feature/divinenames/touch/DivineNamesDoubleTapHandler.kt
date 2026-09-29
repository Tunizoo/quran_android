package com.quran.mobile.feature.divinenames.touch

import android.app.Activity
import android.util.Log
import android.widget.ImageView
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.feature.divinenames.data.DivineNamesDatabase
import com.quran.mobile.feature.divinenames.draw.DivineNamesDrawHelper
import com.quran.mobile.feature.divinenames.ui.DivineNameSheet
import com.quran.page.common.touch.PageDoubleTapHandler
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the explanation sheet when the reader double taps a highlighted name or attribute of Allah
 * on the page. Hit testing uses the rectangles the draw helper already cached for the page, so it
 * is synchronous; only the explanation text is fetched in the background.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class DivineNamesDoubleTapHandler @Inject constructor(
  private val drawHelper: DivineNamesDrawHelper,
  private val database: DivineNamesDatabase,
  private val appCoroutineScope: AppCoroutineScope
) : PageDoubleTapHandler {

  override fun onDoubleTap(activity: Activity, page: Int, image: ImageView, pageX: Float, pageY: Float): Boolean {
    val occurrence = drawHelper.occurrenceAt(page, image, pageX, pageY)
    Log.i(TAG, "double tap page $page at ($pageX, $pageY): ${occurrence?.nameId ?: "no highlighted word"}")
    if (occurrence == null) return false
    appCoroutineScope.launch {
      val name = database.name(occurrence.nameId) ?: return@launch
      withContext(Dispatchers.Main) {
        if (!activity.isFinishing && !activity.isDestroyed) {
          DivineNameSheet.show(activity, name, occurrence)
        }
      }
    }
    return true
  }

  companion object {
    private const val TAG = "DivineNames"
  }
}
