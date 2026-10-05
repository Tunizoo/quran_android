package com.quran.mobile.feature.tajweed

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

/**
 * User preferences for the tajweed colouring, read from the default shared preferences so the
 * switch in the settings screen writes the same key. Also exposes the reader's night mode values,
 * which the overlay needs to pick text colours that match the page.
 */
@SingleIn(AppScope::class)
class TajweedSettings @Inject constructor(
  @ApplicationContext context: Context
) {
  private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

  @Volatile
  private var enabled = preferences.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

  /** Bumped on every relevant preference change so cached overlays are rebuilt. */
  @Volatile
  var generation: Int = 0
    private set

  // kept as a field: SharedPreferences only holds listeners weakly
  private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
    when (key) {
      KEY_ENABLED -> {
        enabled = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
        generation++
      }
      KEY_NIGHT_MODE, KEY_NIGHT_TEXT_BRIGHTNESS, KEY_NIGHT_BACKGROUND_BRIGHTNESS, KEY_USE_NEW_BACKGROUND -> generation++
    }
  }

  init {
    preferences.registerOnSharedPreferenceChangeListener(listener)
  }

  val isEnabled: Boolean get() = enabled

  val isNightMode: Boolean get() = preferences.getBoolean(KEY_NIGHT_MODE, false)

  /** The colour the reader paints the page glyphs with (black by day, a grey level at night). */
  val textColor: Int
    get() {
      if (!isNightMode) return Color.BLACK
      val text = preferences.getInt(KEY_NIGHT_TEXT_BRIGHTNESS, DEFAULT_NIGHT_TEXT_BRIGHTNESS)
      val background = preferences.getInt(KEY_NIGHT_BACKGROUND_BRIGHTNESS, DEFAULT_NIGHT_BACKGROUND_BRIGHTNESS)
      // same formula as the reader's HighlightingImageView
      val level = min(255, (50 * ln(1.0 + background) + text).toInt())
      return Color.rgb(level, level, level)
    }

  /** The page background colour used when no ancestor view provides a background drawable. */
  val fallbackBackgroundColor: Int
    get() = if (isNightMode) {
      val level = preferences.getInt(KEY_NIGHT_BACKGROUND_BRIGHTNESS, DEFAULT_NIGHT_BACKGROUND_BRIGHTNESS)
      Color.rgb(level, level, level)
    } else {
      DAY_PAGE_BACKGROUND
    }

  companion object {
    const val KEY_ENABLED = "tajweedColors"
    const val DEFAULT_ENABLED = false

    // keys owned by the app's QuranSettings
    private const val KEY_NIGHT_MODE = "nightMode"
    private const val KEY_NIGHT_TEXT_BRIGHTNESS = "nightModeTextBrightness"
    private const val KEY_NIGHT_BACKGROUND_BRIGHTNESS = "nightModeBackgroundBrightness"
    private const val KEY_USE_NEW_BACKGROUND = "useNewBackground"
    private const val DEFAULT_NIGHT_TEXT_BRIGHTNESS = 255
    private const val DEFAULT_NIGHT_BACKGROUND_BRIGHTNESS = 0
    private const val DAY_PAGE_BACKGROUND = 0xFFFFF4CB.toInt()
  }
}
