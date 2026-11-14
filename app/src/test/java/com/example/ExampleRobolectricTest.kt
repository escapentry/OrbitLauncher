package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.SquircleIconRenderer
import com.example.model.AppEntry
import com.example.ui.LauncherViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Orbit", appName)
  }

  @Test
  fun `apps chunk cleanly into horizontal pages`() {
    val apps = (1..25).map { idx -> entry("com.example.app$idx", "App $idx") }

    val pages = LauncherViewModel.chunkIntoHorizontalPages(apps, itemsPerPage = 16)
    assertEquals(2, pages.size)
    assertEquals(16, pages[0].size)
    assertEquals(9, pages[1].size)
  }

  @Test
  fun `search ranks prefix before word and acronym matches`() {
    val apps = listOf(
      entry("com.example.map", "Road Map"),
      entry("com.google.maps", "Google Maps"),
      entry("com.example.gm", "Gmail"),
    )

    val byAcronym = LauncherViewModel.filterAndRankApps(apps, "gm")
    assertEquals(listOf("Gmail", "Google Maps"), byAcronym.map { it.label })
  }

  @Test
  fun `home press navigates to start of apps then far-left time page`() {
    assertEquals(1, LauncherViewModel.resolveHomeTargetPage(currentPage = 3, appPageCount = 3))
    assertEquals(0, LauncherViewModel.resolveHomeTargetPage(currentPage = 1, appPageCount = 3))
    assertEquals(1, LauncherViewModel.resolveHomeTargetPage(currentPage = 0, appPageCount = 3))
  }

  @Test
  fun `dominant color extractor picks vibrant app hue over white or black`() {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(AndroidColor.WHITE)
    for (y in 16 until 48) {
      for (x in 16 until 48) {
        bitmap.setPixel(x, y, AndroidColor.rgb(29, 185, 84))
      }
    }

    val color = SquircleIconRenderer.extractVibrantColor(bitmap)
    bitmap.recycle()

    assertTrue(color.green > color.red && color.green > color.blue)
    assertTrue(color != Color.White)
  }

  @Test
  fun `icon palette extractor yields primary and distinct secondary colors for gradient splash`() {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(AndroidColor.TRANSPARENT)
    for (y in 8 until 32) {
      for (x in 8 until 56) {
        bitmap.setPixel(x, y, AndroidColor.rgb(255, 120, 20)) // vivid orange
      }
    }
    for (y in 32 until 56) {
      for (x in 8 until 56) {
        bitmap.setPixel(x, y, AndroidColor.rgb(30, 144, 255)) // vivid blue
      }
    }

    val (primary, secondary) = SquircleIconRenderer.extractIconPalette(bitmap)
    bitmap.recycle()

    assertTrue(primary != secondary)
  }

  @Test
  fun `wallpaper preset resolves known ids and defaults safely`() {
    assertEquals(
      com.example.data.WallpaperPreset.MIDNIGHT_AURORA,
      com.example.data.WallpaperPreset.fromId("midnight_aurora"),
    )
    assertEquals(
      com.example.data.WallpaperPreset.ORBIT_NEBULA,
      com.example.data.WallpaperPreset.fromId("unknown_id"),
    )
  }

  private fun entry(pkg: String, label: String): AppEntry = AppEntry(
    packageName = pkg,
    activityName = "$pkg.MainActivity",
    componentKey = "$pkg/$pkg.MainActivity",
    label = label,
    normalizedLabel = AppEntry.buildNormalized(label),
    acronym = AppEntry.buildAcronym(label),
    sectionChar = AppEntry.buildSectionChar(label),
    isSystemApp = false,
  )
}
