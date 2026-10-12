package com.quran.page.common.touch

import android.app.Activity
import android.widget.ImageView

/**
 * Extension point for features that react to a single tap on a mushaf page image.
 *
 * Implementations are contributed into a set at the application scope. They are consulted before
 * the default single tap behaviour (toggling the toolbars) when no ayah is selected; the first one
 * returning true consumes the tap.
 */
interface PageTapHandler {
  /**
   * @param activity the activity hosting the page
   * @param page the page number
   * @param image the image view showing the page
   * @param pageX x of the tap in page image pixels (the image's own coordinate system)
   * @param pageY y of the tap in page image pixels
   * @return true when the tap was handled
   */
  fun onSingleTap(activity: Activity, page: Int, image: ImageView, pageX: Float, pageY: Float): Boolean
}
