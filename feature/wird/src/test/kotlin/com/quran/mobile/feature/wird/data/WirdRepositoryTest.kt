package com.quran.mobile.feature.wird.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.wird.db.WirdDatabase
import com.quran.mobile.feature.wird.domain.MushafPageCount
import com.quran.mobile.feature.wird.model.Achievement
import com.quran.mobile.feature.wird.model.ReminderMode
import com.quran.mobile.feature.wird.model.WirdSettings
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class WirdRepositoryTest {
  private val clock = FakeWirdClock()
  private lateinit var repository: WirdRepository

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WirdDatabase.Schema.create(driver)
    repository = WirdRepository(
      database = WirdDatabase(driver),
      clock = clock,
      mushafPageCount = MushafPageCount { MUSHAF_PAGES }
    )
  }

  @Test
  fun `defaults are returned before any settings are saved`() = runTest {
    assertThat(repository.currentSettings()).isEqualTo(WirdSettings.DEFAULT)
    val summary = repository.currentSummary()
    assertThat(summary.todayPages).isEqualTo(0)
    assertThat(summary.goal).isEqualTo(WirdSettings.DEFAULT.pagesPerDay)
    assertThat(summary.khatmaNumber).isEqualTo(1)
    assertThat(summary.mushafPageCount).isEqualTo(MUSHAF_PAGES)
  }

  @Test
  fun `recording a page counts it once per day`() = runTest {
    val first = repository.recordPageView(page = 1)
    val again = repository.recordPageView(page = 1)

    assertThat(first.wasNewPageToday).isTrue()
    assertThat(first.newAchievements).containsExactly(Achievement.FIRST_PAGE)
    assertThat(again.wasNewPageToday).isFalse()
    assertThat(again.newAchievements).isEmpty()
    assertThat(repository.currentSummary().todayPages).isEqualTo(1)
  }

  @Test
  fun `the same page counts again on a new day`() = runTest {
    repository.recordPageView(page = 1)
    clock.advanceDays(1)
    val result = repository.recordPageView(page = 1)

    assertThat(result.wasNewPageToday).isTrue()
    val summary = repository.currentSummary()
    assertThat(summary.todayPages).isEqualTo(1)
    assertThat(summary.totalPagesRead).isEqualTo(2)
  }

  @Test
  fun `reaching the goal unlocks the goal achievement and starts a streak`() = runTest {
    repository.updateSettings { it.copy(pagesPerDay = 2) }
    repository.recordPageView(page = 1)
    val result = repository.recordPageView(page = 2)

    assertThat(result.newAchievements).contains(Achievement.FIRST_GOAL)
    val summary = repository.currentSummary()
    assertThat(summary.isTodayGoalMet).isTrue()
    assertThat(summary.currentStreak).isEqualTo(1)
  }

  @Test
  fun `streak achievement unlocks after three consecutive goal days`() = runTest {
    repository.updateSettings { it.copy(pagesPerDay = 1) }
    repository.recordPageView(page = 1)
    clock.advanceDays(1)
    repository.recordPageView(page = 2)
    clock.advanceDays(1)
    val result = repository.recordPageView(page = 3)

    assertThat(result.newAchievements).contains(Achievement.STREAK_3)
    assertThat(repository.currentSummary().currentStreak).isEqualTo(3)
  }

  @Test
  fun `khatma completes when every page of the mushaf has been read`() = runTest {
    for (page in 1 until MUSHAF_PAGES) {
      assertThat(repository.recordPageView(page).completedKhatmaNumber).isNull()
    }
    val summaryBefore = repository.currentSummary()
    assertThat(summaryBefore.khatmaPagesRead).isEqualTo(MUSHAF_PAGES - 1)

    val result = repository.recordPageView(MUSHAF_PAGES)
    assertThat(result.completedKhatmaNumber).isEqualTo(1)
    assertThat(result.newAchievements).contains(Achievement.KHATMA_1)

    val summary = repository.currentSummary()
    assertThat(summary.completedKhatmas).hasSize(1)
    assertThat(summary.khatmaNumber).isEqualTo(2)
    assertThat(summary.khatmaPagesRead).isEqualTo(0)
  }

  @Test
  fun `pages read after a khatma count toward the next one`() = runTest {
    for (page in 1..MUSHAF_PAGES) repository.recordPageView(page)
    clock.advanceDays(1)
    repository.recordPageView(page = 1)

    val summary = repository.currentSummary()
    assertThat(summary.khatmaNumber).isEqualTo(2)
    assertThat(summary.khatmaPagesRead).isEqualTo(1)
  }

  @Test
  fun `fajr reader unlocks for an early morning page`() = runTest {
    clock.setMinuteOfDay(5 * 60)
    val result = repository.recordPageView(page = 10)
    assertThat(result.newAchievements).contains(Achievement.FAJR_READER)
  }

  @Test
  fun `settings are clamped and persisted`() = runTest {
    val updated = repository.updateSettings {
      it.copy(
        pagesPerDay = 500,
        reminderEnabled = true,
        reminderMinuteOfDay = 25 * 60,
        reminderMode = ReminderMode.ADAPTIVE
      )
    }
    assertThat(updated.pagesPerDay).isEqualTo(WirdSettings.MAX_PAGES_PER_DAY)
    assertThat(updated.reminderMinuteOfDay).isEqualTo(24 * 60 - 1)
    assertThat(repository.currentSettings()).isEqualTo(updated)
  }

  @Test
  fun `recent first minutes are reported newest first`() = runTest {
    clock.setMinuteOfDay(9 * 60)
    repository.recordPageView(page = 1)
    clock.setMinuteOfDay(11 * 60)
    repository.recordPageView(page = 2)
    clock.advanceDays(1)
    clock.setMinuteOfDay(7 * 60)
    repository.recordPageView(page = 3)

    assertThat(repository.recentFirstMinutes(14)).containsExactly(7 * 60, 9 * 60).inOrder()
  }

  @Test
  fun `summary flow emits when a page is recorded`() = runTest {
    repository.summary.test {
      assertThat(awaitItem().todayPages).isEqualTo(0)
      repository.recordPageView(page = 1)
      assertThat(awaitItem().todayPages).isEqualTo(1)
      cancelAndIgnoreRemainingEvents()
    }
  }

  companion object {
    private const val MUSHAF_PAGES = 5
  }
}
