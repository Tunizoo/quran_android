package com.quran.mobile.feature.memorization.draw

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.preference.PreferenceManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.ln
import kotlin.math.min

/** The reader's page colours (day / night) read from the app preferences, used to cover words. */
@SingleIn(AppScope::class)
class MemorizationPageColors @Inject constructor(@ApplicationContext context: Context) {
  private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

  val isNightMode: Boolean get() = preferences.getBoolean(KEY_NIGHT_MODE, false)

  /** The colour the reader paints the page glyphs with (black by day, a grey level at night). */
  val textColor: Int
    get() {
      if (!isNightMode) return Color.BLACK
      val text = preferences.getInt(KEY_NIGHT_TEXT_BRIGHTNESS, DEFAULT_NIGHT_TEXT_BRIGHTNESS)
      val background = preferences.getInt(KEY_NIGHT_BACKGROUND_BRIGHTNESS, DEFAULT_NIGHT_BACKGROUND_BRIGHTNESS)
      val level = min(255, (50 * ln(1.0 + background) + text).toInt())
      return Color.rgb(level, level, level)
    }

  /** The page background colour used when no ancestor view provides a plain colour. */
  val fallbackBackgroundColor: Int
    get() = if (isNightMode) {
      val level = preferences.getInt(KEY_NIGHT_BACKGROUND_BRIGHTNESS, DEFAULT_NIGHT_BACKGROUND_BRIGHTNESS)
      Color.rgb(level, level, level)
    } else {
      DAY_PAGE_BACKGROUND
    }

  companion object {
    private const val KEY_NIGHT_MODE = "nightMode"
    private const val KEY_NIGHT_TEXT_BRIGHTNESS = "nightModeTextBrightness"
    private const val KEY_NIGHT_BACKGROUND_BRIGHTNESS = "nightModeBackgroundBrightness"
    private const val DEFAULT_NIGHT_TEXT_BRIGHTNESS = 255
    private const val DEFAULT_NIGHT_BACKGROUND_BRIGHTNESS = 0
    private const val DAY_PAGE_BACKGROUND = 0xFFFFF4CB.toInt()
  }
}
