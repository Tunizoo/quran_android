package com.quran.labs.androidquran.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.QuranAdvancedPreferenceActivity
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.pageselect.PageSelectActivity
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.util.OrientationLockUtils
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.feature.downloadmanager.AudioManagerActivity
import dev.zacsweers.metro.Inject
import java.util.Locale

class QuranSettingsFragment : PreferenceFragmentCompat() {

  @Inject
  lateinit var pageTypes: Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>

  @Inject
  lateinit var extraPreferences: Set<@JvmSuppressWildcards ExtraPreferencesProvider>

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    addPreferencesFromResource(R.xml.quran_preferences)

    val appContext = requireContext().applicationContext

    // field injection
    (appContext as QuranApplication).applicationComponent.inject(this)

    if (!OrientationLockUtils.isOrientationLockSupported(resources.configuration)) {
      hideUnsupportedOrientationPreferences()
    }

    // app language: every language the app ships, applied through the per-app locale API so
    // that the whole interface (including the new features) switches at once
    val languagePref: ListPreference? = findPreference(LANGUAGE_KEY)
    languagePref?.let { pref ->
      val systemDefault = getString(R.string.prefs_app_language_system)
      pref.entries = (listOf(systemDefault) + SUPPORTED_LANGUAGES.map { nativeName(it) }).toTypedArray()
      pref.entryValues = (listOf("") + SUPPORTED_LANGUAGES).toTypedArray()
      pref.value = currentLanguageTag()
      pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
        val tag = newValue as String
        AppCompatDelegate.setApplicationLocales(
          if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
        true
      }
    }

    // handle theme preference
    val themePref: Preference? = findPreference(Constants.PREF_APP_THEME)
    themePref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
      ThemeUtil.setTheme(newValue as String)
      true
    }

    // handle translation manager click
    val translationPref: Preference? = findPreference(Constants.PREF_TRANSLATION_MANAGER)
    translationPref?.setOnPreferenceClickListener {
      startActivity(Intent(activity, TranslationManagerActivity::class.java))
      true
    }

    // handle audio manager click
    val audioManagerPref: Preference? = findPreference(Constants.PREF_AUDIO_MANAGER)
    audioManagerPref?.setOnPreferenceClickListener {
      startActivity(Intent(activity, AudioManagerActivity::class.java))
      true
    }

    val pageChangePref: Preference? = findPreference(Constants.PREF_PAGE_TYPE)
    if (pageTypes.size < 2 && pageChangePref != null) {
      val readingPrefs: Preference? = findPreference(Constants.PREF_READING_CATEGORY)
      (readingPrefs as PreferenceGroup).removePreference(pageChangePref)
    }

    // add additional injected preferences (if any)
    extraPreferences
      .sortedBy { it.order }
      .forEach { it.addPreferences(preferenceScreen) }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view = super.onCreateView(inflater, container, savedInstanceState)
    val recyclerView = listView
    recyclerView.clipToPadding = false
    ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      recyclerView.updateLayoutParams<ViewGroup.LayoutParams> {
        // top, left, right are handled by QuranActivity
        view.setPadding(0, 0, 0, insets.bottom)
      }

      windowInsets
    }
    return view
  }

  override fun onPreferenceTreeClick(preference: Preference): Boolean {
    val key = preference.key
    if ("key_prefs_advanced" == key) {
      val intent = Intent(activity, QuranAdvancedPreferenceActivity::class.java)
      startActivity(intent)
      return true
    } else if (Constants.PREF_PAGE_TYPE == key) {
      val intent = Intent(activity, PageSelectActivity::class.java)
      startActivity(intent)
      return true
    }

    for (extraPref in extraPreferences) {
      if (extraPref.onPreferenceClick(preference)) {
        return true
      }
    }

    return super.onPreferenceTreeClick(preference)
  }

  /** The language tag the user picked for the app, or "" when the app follows the system. */
  private fun currentLanguageTag(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty()) return ""
    val language = locales.get(0)?.language ?: return ""
    return SUPPORTED_LANGUAGES.firstOrNull { it == language || it.startsWith("$language-") } ?: ""
  }

  /** The name of a language written in that language, e.g. "Français", "العربية". */
  private fun nativeName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val name = locale.getDisplayLanguage(locale)
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
  }

  private fun hideUnsupportedOrientationPreferences() {
    val displayPrefs = findPreference<PreferenceGroup>(getString(R.string.prefs_display_category_key))
    val lockOrientationPref = findPreference<Preference>(Constants.PREF_LOCK_ORIENTATION)
    val landscapeOrientationPref = findPreference<Preference>(Constants.PREF_LANDSCAPE_ORIENTATION)

    if (displayPrefs != null && lockOrientationPref != null) {
      displayPrefs.removePreference(lockOrientationPref)
    }
    if (displayPrefs != null && landscapeOrientationPref != null) {
      displayPrefs.removePreference(landscapeOrientationPref)
    }
  }

  companion object {
    private const val LANGUAGE_KEY = "appLanguage"

    /** Languages the app has translations for (see localeFilters in app/build.gradle.kts). */
    private val SUPPORTED_LANGUAGES = listOf(
      "ar", "en", "az", "bg", "bn", "bs", "cs", "da", "de", "el", "es", "et", "fa", "fi", "fr", "hi", "hr",
      "hu", "in", "it", "ja", "kk", "ko", "ku", "lt", "lv", "ms", "nl", "pl", "ps", "pt", "ro", "ru", "sk",
      "sl", "sq", "sr", "sv", "th", "tr", "ug", "uk", "ur", "uz", "vi", "zh"
    )
  }
}
