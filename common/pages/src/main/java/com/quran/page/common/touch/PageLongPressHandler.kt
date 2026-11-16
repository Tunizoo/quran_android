package com.quran.page.common.touch

import android.app.Activity
import android.widget.ImageView

/**
 * Extension point for features that react to a long press on a mushaf page image.
 *
 * Implementations are contributed into a set at the application scope. They are consulted before
 * the default long press behaviour (selecting the ayah); the first one returning true consumes the
 * press. The gesture is the reader's own long press, so the duration is the same.
 */
interface PageLongPressHandler {
  /**
   * @param activity the activity hosting the page
   * @param page the page number
   * @param image the image view showing the page
   * @param pageX x of the press in page image pixels (the image's own coordinate system)
   * @param pageY y of the press in page image pixels
   * @return true when the press was handled
   */
  fun onLongPress(activity: Activity, page: Int, image: ImageView, pageX: Float, pageY: Float): Boolean
}
