package com.quran.mobile.feature.memorization.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.memorization.di.MemorizationComponentInterface
import com.quran.mobile.feature.memorization.presenter.MemorizationPresenter
import com.quran.page.common.data.QuranNaming
import dev.zacsweers.metro.Inject

class MemorizationActivity : ComponentActivity() {

  @Inject
  lateinit var presenter: MemorizationPresenter

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var naming: QuranNaming

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? MemorizationComponentInterface
    injector?.memorizationComponentFactory()?.generate()?.inject(this)
    if (!::presenter.isInitialized) {
      finish()
      return
    }

    enableEdgeToEdge()
    setContent {
      QuranTheme {
        val moleculeScope = rememberCoroutineScope()
        val stateFlow = remember {
          moleculeScope.launchMolecule(mode = RecompositionMode.ContextClock) {
            presenter.present()
          }
        }
        val state by stateFlow.collectAsState()
        MemorizationScreen(
          state = state,
          quranInfo = quranInfo,
          naming = naming,
          onBackPressed = { finish() }
        )
      }
    }
  }
}
