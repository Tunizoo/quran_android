package com.quran.mobile.feature.divinenames.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.divinenames.data.DivineNamesDatabase
import com.quran.mobile.feature.divinenames.di.DivineNamesComponentInterface
import dev.zacsweers.metro.Inject

class DivineNamesActivity : ComponentActivity() {

  @Inject
  lateinit var database: DivineNamesDatabase

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? DivineNamesComponentInterface
    injector?.divineNamesComponentFactory()?.generate()?.inject(this)
    if (!::database.isInitialized) {
      finish()
      return
    }

    val initialNameId = intent.getStringExtra(EXTRA_NAME_ID)
    enableEdgeToEdge()
    setContent {
      QuranTheme {
        DivineNamesScreen(
          database = database,
          initialNameId = initialNameId,
          onBackPressed = { finish() }
        )
      }
    }
  }

  companion object {
    const val EXTRA_NAME_ID = "name_id"

    fun intent(context: Context, nameId: String? = null): Intent =
      Intent(context, DivineNamesActivity::class.java).apply {
        if (nameId != null) putExtra(EXTRA_NAME_ID, nameId)
      }
  }
}
