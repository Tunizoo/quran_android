package com.quran.labs.androidquran.util

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.quran.labs.androidquran.data.Constants

object ThemeUtil {

  /**
   * Applies the app theme: the reader's night mode darkens the whole app (lists, toolbars,
   * panels), otherwise the "app theme" preference (light / dark / follow the system) decides.
   */
  fun applyFromPreferences(prefs: SharedPreferences) {
    if (prefs.getBoolean(Constants.PREF_NIGHT_MODE, false)) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    } else {
      setTheme(prefs.getString(Constants.PREF_APP_THEME, Constants.THEME_DEFAULT) ?: Constants.THEME_DEFAULT)
    }
  }

  fun setTheme(theme: String) {
    val mappedTheme = when (theme) {
      Constants.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
      Constants.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
      Constants.THEME_DEFAULT -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
      else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    AppCompatDelegate.setDefaultNightMode(mappedTheme)
  }
}
