plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.feature.tajweed"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:glyphbounds"))
  implementation(project(":common:pages"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.preference.ktx)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // testing
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
