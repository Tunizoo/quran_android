package com.quran.mobile.feature.memorization.touch

import android.app.Activity
import android.widget.ImageView
import com.quran.data.di.AppScope
import com.quran.mobile.feature.memorization.draw.MemorizationDrawHelper
import com.quran.mobile.feature.memorization.session.MemorizationSession
import com.quran.page.common.touch.PageTapHandler
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** A single tap on a covered word reveals that word (a "peek") while a session is running. */
@ContributesIntoSet(AppScope::class)
class MemorizationTapHandler @Inject constructor(
  private val drawHelper: MemorizationDrawHelper,
  private val session: MemorizationSession
) : PageTapHandler {

  override fun onSingleTap(activity: Activity, page: Int, image: ImageView, pageX: Float, pageY: Float): Boolean {
    val word = drawHelper.hiddenWordAt(page, image, pageX, pageY) ?: return false
    session.peek(word)
    return true
  }
}
