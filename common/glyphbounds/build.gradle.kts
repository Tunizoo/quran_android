plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.common.glyphbounds"

dependencies {
  implementation(project(":common:data"))
}
