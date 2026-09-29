package com.quran.mobile.feature.wird

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
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.wird.di.WirdComponentInterface
import com.quran.mobile.feature.wird.presenter.WirdPresenter
import com.quran.mobile.feature.wird.ui.WirdScreen
import dev.zacsweers.metro.Inject

class WirdActivity : ComponentActivity() {

  @Inject
  lateinit var presenter: WirdPresenter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? WirdComponentInterface
    injector?.wirdComponentFactory()?.generate()?.inject(this)
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
        WirdScreen(state = state, onBackPressed = { finish() })
      }
    }
  }
}
