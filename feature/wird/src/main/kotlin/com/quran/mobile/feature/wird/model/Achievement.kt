package com.quran.mobile.feature.wird.model

/**
 * The set of achievements the wird feature can unlock. [id] is the stable key persisted in the
 * database - never change it for an existing entry.
 */
enum class Achievement(val id: String) {
  FIRST_PAGE("first_page"),
  FIRST_GOAL("first_goal"),
  STREAK_3("streak_3"),
  STREAK_7("streak_7"),
  STREAK_30("streak_30"),
  STREAK_100("streak_100"),
  PAGES_100("pages_100"),
  PAGES_300("pages_300"),
  PAGES_1000("pages_1000"),
  JUZ_IN_A_DAY("juz_in_a_day"),
  FAJR_READER("fajr_reader"),
  KHATMA_1("khatma_1"),
  KHATMA_3("khatma_3");

  companion object {
    fun fromId(id: String): Achievement? = entries.firstOrNull { it.id == id }
  }
}
