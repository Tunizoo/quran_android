plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.feature.divinenames"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:pages"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.material)

  // compose
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // testing
  testImplementation(project(":common:test-utils"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
