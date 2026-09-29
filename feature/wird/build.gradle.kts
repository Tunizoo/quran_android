plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.feature.wird"

sqldelight {
  databases {
    create("WirdDatabase") {
      packageName.set("com.quran.mobile.feature.wird.db")
    }
  }
}

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.androidx.work.runtime.ktx)

  // compose
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  // immutable collections
  implementation(libs.kotlinx.collections.immutable)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // molecule
  implementation(libs.molecule)

  // sqldelight
  implementation(libs.sqldelight.android.driver)
  implementation(libs.sqldelight.coroutines.extensions)

  // testing
  testImplementation(project(":common:test-utils"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.sqldelight.sqlite.driver)
}
