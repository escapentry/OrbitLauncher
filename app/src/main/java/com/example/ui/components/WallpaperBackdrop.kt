package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.WallpaperPreset
import com.example.data.WallpaperState

@Composable
fun LauncherAtmosphericBackground(
  wallpaperState: WallpaperState,
  blurMixProvider: () -> Float,
  scaleProvider: () -> Float,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer {
        val s = scaleProvider()
        scaleX = s
        scaleY = s
      },
  ) {
    // Crisp sharp wallpaper (visible on Far-Left Time Page)
    WallpaperLayerContent(
      wallpaperState = wallpaperState,
      modifier = Modifier.fillMaxSize(),
    )

    // Heavy frosted blur layer GPU-crossfaded across App Pages (1+)
    val isNativeBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurModifier =
      if (isNativeBlurSupported) {
        Modifier
          .fillMaxSize()
          .blur(56.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
      } else {
        Modifier.fillMaxSize()
      }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = blurMixProvider() },
    ) {
      WallpaperLayerContent(
        wallpaperState = wallpaperState,
        modifier = blurModifier,
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = if (isNativeBlurSupported) {
                listOf(
                  Color(0x78060A12),
                  Color(0x90060A12),
                  Color(0xAC060A12),
                )
              } else {
                listOf(
                  Color(0xB4060A12),
                  Color(0xC6060A12),
                  Color(0xD8060A12),
                )
              }
            )
          ),
      )
    }
  }
}

@Composable
fun WallpaperLayerContent(
  wallpaperState: WallpaperState,
  modifier: Modifier = Modifier,
) {
  val customBmp = wallpaperState.customBitmap
  if (wallpaperState.activePreset == WallpaperPreset.CUSTOM_PHOTO && customBmp != null) {
    Image(
      bitmap = customBmp,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      filterQuality = FilterQuality.High,
      modifier = modifier,
    )
  } else {
    WallpaperPresetArtwork(
      preset = wallpaperState.activePreset,
      modifier = modifier,
    )
  }
}

@Composable
private fun WallpaperPresetArtwork(
  preset: WallpaperPreset,
  modifier: Modifier = Modifier,
) {
  when (preset) {
    WallpaperPreset.ORBIT_NEBULA,
    WallpaperPreset.CUSTOM_PHOTO -> {
      Image(
        painter = painterResource(id = R.drawable.img_launcher_backdrop),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
      )
    }

    WallpaperPreset.MIDNIGHT_AURORA -> {
      AtmosphericMeshCanvas(
        baseColors = listOf(Color(0xFF060B17), Color(0xFF0B1F3A), Color(0xFF071321)),
        primaryGlow = Color(0xAA10B981),
        secondaryGlow = Color(0x9922D3EE),
        accentGlow = Color(0x886366F1),
        modifier = modifier,
      )
    }

    WallpaperPreset.OBSIDIAN_DUSK -> {
      AtmosphericMeshCanvas(
        baseColors = listOf(Color(0xFF0B0A16), Color(0xFF231738), Color(0xFF140D1B)),
        primaryGlow = Color(0x99F59E0B),
        secondaryGlow = Color(0x88EC4899),
        accentGlow = Color(0x887C3AED),
        modifier = modifier,
      )
    }

    WallpaperPreset.EMERALD_MIST -> {
      AtmosphericMeshCanvas(
        baseColors = listOf(Color(0xFF051014), Color(0xFF0D2E35), Color(0xFF07191E)),
        primaryGlow = Color(0x992DD4BF),
        secondaryGlow = Color(0x880EA5E9),
        accentGlow = Color(0x7714B8A6),
        modifier = modifier,
      )
    }

    WallpaperPreset.NOCTURNE_SAPPHIRE -> {
      AtmosphericMeshCanvas(
        baseColors = listOf(Color(0xFF050A18), Color(0xFF0F2452), Color(0xFF091229)),
        primaryGlow = Color(0xAA3B82F6),
        secondaryGlow = Color(0x8838BDF8),
        accentGlow = Color(0x884F46E5),
        modifier = modifier,
      )
    }
  }
}

@Composable
private fun AtmosphericMeshCanvas(
  baseColors: List<Color>,
  primaryGlow: Color,
  secondaryGlow: Color,
  accentGlow: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    drawRect(
      brush = Brush.verticalGradient(colors = baseColors),
      size = size,
    )
    val maxDim = maxOf(size.width, size.height)
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(primaryGlow, Color.Transparent),
        center = Offset(size.width * 0.22f, size.height * 0.24f),
        radius = maxDim * 0.62f,
      ),
      radius = maxDim * 0.62f,
      center = Offset(size.width * 0.22f, size.height * 0.24f),
    )
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(secondaryGlow, Color.Transparent),
        center = Offset(size.width * 0.85f, size.height * 0.42f),
        radius = maxDim * 0.56f,
      ),
      radius = maxDim * 0.56f,
      center = Offset(size.width * 0.85f, size.height * 0.42f),
    )
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(accentGlow, Color.Transparent),
        center = Offset(size.width * 0.45f, size.height * 0.88f),
        radius = maxDim * 0.65f,
      ),
      radius = maxDim * 0.65f,
      center = Offset(size.width * 0.45f, size.height * 0.88f),
    )
  }
}

@Composable
fun WallpaperPickerSheetContent(
  wallpaperState: WallpaperState,
  onSelectPreset: (WallpaperPreset) -> Unit,
  onChooseCustomPhoto: () -> Unit,
  onUseAsDefaultHome: () -> Unit,
) {
  val presetList = buildList {
    if (wallpaperState.customBitmap != null) {
      add(WallpaperPreset.CUSTOM_PHOTO to stringResource(id = R.string.wallpaper_preset_custom))
    }
    add(WallpaperPreset.ORBIT_NEBULA to stringResource(id = R.string.wallpaper_preset_orbit))
    add(WallpaperPreset.MIDNIGHT_AURORA to stringResource(id = R.string.wallpaper_preset_aurora))
    add(WallpaperPreset.OBSIDIAN_DUSK to stringResource(id = R.string.wallpaper_preset_dusk))
    add(WallpaperPreset.EMERALD_MIST to stringResource(id = R.string.wallpaper_preset_emerald))
    add(WallpaperPreset.NOCTURNE_SAPPHIRE to stringResource(id = R.string.wallpaper_preset_nocturne))
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 24.dp)
      .padding(bottom = 26.dp)
      .testTag("wallpaper_picker_sheet"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(id = R.string.wallpaper_menu_title),
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
          color = Color.White,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(id = R.string.wallpaper_menu_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White.copy(alpha = 0.65f),
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Surface(
        color = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier
          .clip(CircleShape)
          .clickable(onClick = onUseAsDefaultHome)
          .testTag("use_as_default_home_button"),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Home,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(id = R.string.set_as_default_home),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
    ) {
      presetList.forEach { (preset, label) ->
        val selected = wallpaperState.activePreset == preset
        val previewState = wallpaperState.copy(activePreset = preset)

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .width(82.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onSelectPreset(preset) }
            .padding(4.dp)
            .testTag("wallpaper_preset_${preset.id}"),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(width = 74.dp, height = 114.dp)
              .clip(RoundedCornerShape(16.dp))
              .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) Color(0xFF22D3EE) else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(16.dp),
              ),
          ) {
            WallpaperLayerContent(
              wallpaperState = previewState,
              modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(28.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF22D3EE)),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = null,
                  tint = Color(0xFF06101B),
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Button(
      onClick = onChooseCustomPhoto,
      shape = RoundedCornerShape(18.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .testTag("wallpaper_choose_photo_button"),
    ) {
      Icon(
        imageVector = Icons.Rounded.PhotoLibrary,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = stringResource(id = R.string.wallpaper_choose_photo),
        style = MaterialTheme.typography.titleMedium,
      )
    }
  }
}
