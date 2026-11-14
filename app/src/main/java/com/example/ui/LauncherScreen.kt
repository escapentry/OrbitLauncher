package com.example.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.RenderedSquircleIcon
import com.example.display.DisplayAutoScaler
import com.example.display.DisplayProfile
import com.example.model.AppEntry
import com.example.ui.components.LauncherAtmosphericBackground
import com.example.ui.components.MaterialSplashTransitionOverlay
import com.example.ui.components.OrbitFirstLaunchLoadingOverlay
import com.example.ui.components.SquircleAppBadge
import com.example.ui.components.SquircleAppItem
import com.example.ui.components.WallpaperPickerSheetContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
  viewModel: LauncherViewModel,
  homePressSignal: Int,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val hostView = LocalView.current
  val haptics = LocalHapticFeedback.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val hardwareInfo = remember(context) { DisplayAutoScaler.resolveHardwareMetrics(context) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { pickedUri ->
    if (pickedUri != null) {
      viewModel.saveCustomWallpaper(pickedUri)
    }
  }

  val isTransitionActive =
    uiState.transitionState.phase == LaunchTransitionPhase.LAUNCHING ||
      uiState.transitionState.phase == LaunchTransitionPhase.LAUNCHED_WAITING_RETURN

  val workspaceScale by animateFloatAsState(
    targetValue = if (isTransitionActive) 0.96f else 1.0f,
    animationSpec = spring(
      dampingRatio = 0.86f,
      stiffness = Spring.StiffnessMedium,
    ),
    label = "workspace_gpu_scale",
  )

  var rootWidthPx by remember { mutableFloatStateOf(0f) }
  var rootHeightPx by remember { mutableFloatStateOf(0f) }

  val readyState = uiState.loadState as? AppsLoadState.Ready
  val appPages = readyState?.pages.orEmpty()
  val totalPages = 1 + appPages.size

  val pagerState = rememberPagerState(
    initialPage = 0,
    pageCount = { totalPages },
  )
  var hasPositionedInitialPage by remember { mutableStateOf(false) }
  LaunchedEffect(readyState != null, appPages.size) {
    if (readyState != null && !hasPositionedInitialPage) {
      hasPositionedInitialPage = true
      if (appPages.isNotEmpty()) {
        pagerState.scrollToPage(1)
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF080E1F))
      .onSizeChanged { size ->
        rootWidthPx = size.width.toFloat()
        rootHeightPx = size.height.toFloat()
      },
  ) {
    // Dual-layer wallpaper: crisp on far-left Time page (0), GPU-blurred on App pages (1+)
    LauncherAtmosphericBackground(
      wallpaperState = uiState.wallpaperState,
      blurMixProvider = {
        if (readyState == null) {
          0f
        } else {
          (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
        }
      },
      scaleProvider = { 1f + ((1f - workspaceScale) * 0.5f) },
    )

    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding(),
    ) {
      val profile = remember(
        maxWidth,
        maxHeight,
        density.density,
        density.fontScale,
        hardwareInfo,
      ) {
        DisplayAutoScaler.computeProfile(
          availableWidthDp = maxWidth.value,
          availableHeightDp = maxHeight.value,
          density = density.density,
          systemFontScale = density.fontScale,
          hardwareInfo = hardwareInfo,
        )
      }

      LaunchedEffect(profile.itemsPerPage, profile.dockColumns, profile.iconRenderPx) {
        viewModel.updateLayoutMetrics(
          itemsPerPage = profile.itemsPerPage,
          dockColumns = profile.dockColumns,
          targetIconRenderPx = profile.iconRenderPx,
        )
      }

      val currentTimeMillis = rememberLiveClockMillis()
      val launchingKey =
        if (isTransitionActive) {
          uiState.transitionState.componentKey
        } else {
          null
        }
      val returningKey =
        if (uiState.transitionState.phase == LaunchTransitionPhase.CLOSING) {
          uiState.transitionState.componentKey
        } else {
          null
        }

      if (readyState != null) {
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(homePressSignal) {
          if (homePressSignal > 0) {
            focusManager.clearFocus()
            keyboardController?.hide()
            when (val action = viewModel.handleHomeButtonPressed(pagerState.currentPage, appPages.size)) {
              is HomeNavigationAction.ScrollToPage -> {
                pagerState.animateScrollToPage(
                  page = action.targetPage,
                  animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                )
              }
              HomeNavigationAction.None -> Unit
            }
          }
        }

        BackHandler(enabled = true) {
          when {
            uiState.isWallpaperMenuOpen -> viewModel.dismissWallpaperMenu()
            uiState.selectedAppForActions != null -> viewModel.dismissAppActions()
            uiState.searchQuery.isNotEmpty() -> {
              viewModel.clearSearch()
              focusManager.clearFocus()
              keyboardController?.hide()
            }
            pagerState.currentPage != 1 && appPages.isNotEmpty() -> {
              coroutineScope.launch {
                pagerState.animateScrollToPage(
                  page = 1,
                  animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                )
              }
            }
          }
        }

        val openWallpaperWithHaptics = remember(viewModel, haptics) {
          {
            focusManager.clearFocus()
            keyboardController?.hide()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.openWallpaperMenu()
          }
        }

        Column(
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
              scaleX = workspaceScale
              scaleY = workspaceScale
            },
        ) {
          HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = uiState.searchQuery.isEmpty() && uiState.transitionState.phase == LaunchTransitionPhase.IDLE,
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .testTag("horizontal_apps_pager"),
          ) { pageIndex ->
            if (pageIndex == 0) {
              // Far-left empty homescreen: hold blank area to open Wallpaper menu
              FarLeftTimeOnlyPage(
                profile = profile,
                currentTimeMillis = currentTimeMillis,
                onLongPressBlankSpace = openWallpaperWithHaptics,
                modifier = Modifier
                  .fillMaxSize()
                  .testTag("far_left_time_page"),
              )
            } else {
              val appPageIndex = pageIndex - 1
              val pageApps = appPages.getOrElse(appPageIndex) { emptyList() }
              val singleMatch =
                if (uiState.searchQuery.isNotBlank() && readyState.searchResults.size == 1) {
                  readyState.searchResults.first()
                } else {
                  null
                }

              Column(modifier = Modifier.fillMaxSize()) {
                AppPagesSearchBar(
                  query = uiState.searchQuery,
                  profile = profile,
                  singleMatch = singleMatch,
                  onQueryChange = viewModel::onSearchQueryChanged,
                  onClearSearch = {
                    viewModel.clearSearch()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                  },
                  onSubmitSearch = {
                    if (viewModel.launchTopMatchIfSingleOrExact()) {
                      focusManager.clearFocus()
                      keyboardController?.hide()
                    }
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = profile.horizontalPaddingDp)
                    .padding(top = 10.dp, bottom = 6.dp),
                )

                if (uiState.searchQuery.isNotBlank()) {
                  SearchResultsGrid(
                    results = readyState.searchResults,
                    profile = profile,
                    launchingComponentKey = launchingKey,
                    returningComponentKey = returningKey,
                    onLaunchApp = { entry, bounds, asset ->
                      focusManager.clearFocus()
                      keyboardController?.hide()
                      viewModel.launchApp(entry, hostView, bounds, asset)
                    },
                    onLongPressApp = viewModel::showAppActions,
                    getCachedIcon = viewModel::getCachedIcon,
                    loadIcon = viewModel::loadIcon,
                    modifier = Modifier
                      .weight(1f)
                      .fillMaxWidth(),
                  )
                } else if (pageApps.isEmpty()) {
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                      .weight(1f)
                      .fillMaxWidth()
                      .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { openWallpaperWithHaptics() })
                      },
                  ) {
                    Text(
                      text = stringResource(id = R.string.no_apps_found),
                      style = MaterialTheme.typography.titleMedium,
                      color = Color.White.copy(alpha = 0.72f),
                    )
                  }
                } else {
                  HorizontalAppPage(
                    apps = pageApps,
                    profile = profile,
                    launchingComponentKey = launchingKey,
                    returningComponentKey = returningKey,
                    onLaunchApp = { entry, bounds, asset ->
                      focusManager.clearFocus()
                      keyboardController?.hide()
                      viewModel.launchApp(entry, hostView, bounds, asset)
                    },
                    onLongPressApp = viewModel::showAppActions,
                    onLongPressBlankSpace = openWallpaperWithHaptics,
                    getCachedIcon = viewModel::getCachedIcon,
                    loadIcon = viewModel::loadIcon,
                    modifier = Modifier
                      .weight(1f)
                      .fillMaxWidth(),
                  )
                }
              }
            }
          }

          if (totalPages > 1 && uiState.searchQuery.isEmpty()) {
            ApplePageIndicator(
              pagerState = pagerState,
              pageCount = totalPages,
              onSelectPage = { target ->
                coroutineScope.launch {
                  pagerState.animateScrollToPage(
                    page = target,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                  )
                }
              },
              modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp),
            )
          } else {
            Spacer(modifier = Modifier.height(8.dp))
          }

          if (readyState.dockApps.isNotEmpty() && uiState.searchQuery.isEmpty()) {
            AppleFrostedDock(
              dockApps = readyState.dockApps,
              dockGradientColors = readyState.dockGradientColors,
              profile = profile,
              launchingComponentKey = launchingKey,
              returningComponentKey = returningKey,
              onLaunchApp = { entry, bounds, asset ->
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.launchApp(entry, hostView, bounds, asset)
              },
              onLongPressApp = viewModel::showAppActions,
              getCachedIcon = viewModel::getCachedIcon,
              loadIcon = viewModel::loadIcon,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = profile.horizontalPaddingDp)
                .padding(bottom = 12.dp),
            )
          }
        }
      }

      if (uiState.isWallpaperMenuOpen) {
        val wallpaperSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
          onDismissRequest = viewModel::dismissWallpaperMenu,
          sheetState = wallpaperSheetState,
          containerColor = Color(0xF0141C2B),
          contentColor = Color.White,
          shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
          WallpaperPickerSheetContent(
            wallpaperState = uiState.wallpaperState,
            onSelectPreset = { preset ->
              viewModel.selectWallpaperPreset(preset)
            },
            onChooseCustomPhoto = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            onUseAsDefaultHome = viewModel::openDefaultHomeSettings,
          )
        }
      }

      val actionApp = uiState.selectedAppForActions
      if (actionApp != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isPinnedToDock = actionApp.componentKey in uiState.pinnedDockKeys
        ModalBottomSheet(
          onDismissRequest = viewModel::dismissAppActions,
          sheetState = sheetState,
          containerColor = Color(0xF0182030),
          contentColor = Color.White,
          shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
          AppQuickActionsSheet(
            entry = actionApp,
            isPinnedToDock = isPinnedToDock,
            iconRenderPx = profile.iconRenderPx,
            getCachedIcon = viewModel::getCachedIcon,
            loadIcon = viewModel::loadIcon,
            onLaunch = { viewModel.launchApp(actionApp, hostView) },
            onTogglePinDock = { viewModel.togglePinToDock(actionApp) },
            onAppInfo = { viewModel.openAppInfo(actionApp) },
          )
        }
      }
    }

    // Immediate Orbit icon loading screen on first launch instead of black freeze
    AnimatedVisibility(
      visible = readyState == null,
      enter = fadeIn(tween(120)),
      exit = fadeOut(tween(220, easing = FastOutSlowInEasing)),
    ) {
      OrbitFirstLaunchLoadingOverlay()
    }

    // GPU-accelerated sequenced splash launch & reverse close overlay
    if (rootWidthPx > 0f && rootHeightPx > 0f) {
      MaterialSplashTransitionOverlay(
        transitionState = uiState.transitionState,
        containerWidthPx = rootWidthPx,
        containerHeightPx = rootHeightPx,
        onLaunchAnimationReady = { eventId ->
          viewModel.commitPendingLaunch(eventId = eventId, hostView = hostView)
        },
        onCloseCompleted = viewModel::clearLaunchTransition,
      )
    }
  }
}

@Composable
private fun AppPagesSearchBar(
  query: String,
  profile: DisplayProfile,
  singleMatch: AppEntry?,
  onQueryChange: (String) -> Unit,
  onClearSearch: () -> Unit,
  onSubmitSearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.White.copy(alpha = 0.14f),
    shape = CircleShape,
    modifier = modifier
      .heightIn(min = 48.dp)
      .border(
        width = 0.75.dp,
        color = if (query.isNotEmpty()) {
          Color.White.copy(alpha = 0.48f)
        } else {
          Color.White.copy(alpha = 0.22f)
        },
        shape = CircleShape,
      )
      .testTag("app_pages_search_bar"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.80f),
        modifier = Modifier.size(20.dp),
      )

      Spacer(modifier = Modifier.width(10.dp))

      Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.weight(1f),
      ) {
        if (query.isEmpty()) {
          Text(
            text = stringResource(id = R.string.search_apps_hint),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = profile.searchFontSp),
            color = Color.White.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = profile.searchFontSp,
            color = Color.White,
          ),
          cursorBrush = SolidColor(Color.White),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
          keyboardActions = KeyboardActions(onGo = { onSubmitSearch() }),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("search_input_field"),
        )
      }

      if (singleMatch != null) {
        Surface(
          color = Color.White.copy(alpha = 0.90f),
          contentColor = Color(0xFF0A0F1A),
          shape = CircleShape,
          modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onSubmitSearch)
            .testTag("instant_launch_chip"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Bolt,
              contentDescription = "Open ${singleMatch.label}",
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
              text = "GO",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
          }
        }
      }

      if (query.isNotEmpty()) {
        IconButton(
          onClick = onClearSearch,
          modifier = Modifier.testTag("clear_search_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.clear_search),
            tint = Color.White.copy(alpha = 0.85f),
          )
        }
      }
    }
  }
}

@Composable
private fun SearchResultsGrid(
  results: List<AppEntry>,
  profile: DisplayProfile,
  launchingComponentKey: String?,
  returningComponentKey: String?,
  onLaunchApp: (AppEntry, Rect?, RenderedSquircleIcon?) -> Unit,
  onLongPressApp: (AppEntry) -> Unit,
  getCachedIcon: (String, Int) -> RenderedSquircleIcon?,
  loadIcon: suspend (AppEntry, Int) -> RenderedSquircleIcon?,
  modifier: Modifier = Modifier,
) {
  if (results.isEmpty()) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = modifier,
    ) {
      Text(
        text = stringResource(id = R.string.no_matching_apps),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.75f),
      )
    }
  } else {
    LazyVerticalGrid(
      columns = GridCells.Fixed(profile.columns),
      contentPadding = PaddingValues(
        horizontal = profile.horizontalPaddingDp,
        vertical = 10.dp,
      ),
      horizontalArrangement = Arrangement.spacedBy(profile.gridHorizontalSpacingDp),
      verticalArrangement = Arrangement.spacedBy(profile.gridVerticalSpacingDp),
      modifier = modifier.testTag("search_results_grid"),
    ) {
      items(
        items = results,
        key = { it.componentKey },
      ) { entry ->
        SquircleAppItem(
          entry = entry,
          iconSizeDp = profile.iconSizeDp,
          iconRenderPx = profile.iconRenderPx,
          labelFontSp = profile.labelFontSp,
          labelLineHeightSp = profile.labelLineHeightSp,
          labelMaxLines = profile.labelMaxLines,
          showLabel = true,
          isLaunchingTarget = entry.componentKey == launchingComponentKey,
          isReturningTarget = entry.componentKey == returningComponentKey,
          getCachedIcon = getCachedIcon,
          loadIcon = loadIcon,
          onClick = { bounds, asset -> onLaunchApp(entry, bounds, asset) },
          onLongClick = { onLongPressApp(entry) },
        )
      }
    }
  }
}

@Composable
private fun FarLeftTimeOnlyPage(
  profile: DisplayProfile,
  currentTimeMillis: Long,
  onLongPressBlankSpace: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }

  val timeText = remember(currentTimeMillis, is24Hour) {
    val pattern = if (is24Hour) "HH:mm" else "h:mm"
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(currentTimeMillis))
  }
  val amPmText = remember(currentTimeMillis, is24Hour) {
    if (is24Hour) "" else SimpleDateFormat("a", Locale.getDefault()).format(Date(currentTimeMillis))
  }
  val fullDateText = remember(currentTimeMillis) {
    SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(currentTimeMillis))
  }

  val timeSp = (profile.clockFontSp.value * 1.75f).coerceIn(48f, 84f)
  val dateSp = (profile.dateFontSp.value * 1.18f).coerceIn(15f, 21f)
  val textShadow = remember {
    Shadow(
      color = Color.Black.copy(alpha = 0.55f),
      offset = Offset(0f, 2f),
      blurRadius = 8f,
    )
  }

  // Empty home page: hold anywhere on the blank wallpaper to bring up the Wallpaper menu
  Box(
    contentAlignment = Alignment.TopStart,
    modifier = modifier
      .pointerInput(Unit) {
        detectTapGestures(onLongPress = { onLongPressBlankSpace() })
      }
      .padding(horizontal = profile.horizontalPaddingDp)
      .padding(top = 20.dp),
  ) {
    Column(horizontalAlignment = Alignment.Start) {
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          text = timeText,
          style = MaterialTheme.typography.displayLarge.copy(
            fontSize = TextUnit(timeSp, TextUnitType.Sp),
            lineHeight = TextUnit(timeSp * 1.02f, TextUnitType.Sp),
            fontWeight = FontWeight.Bold,
            shadow = textShadow,
          ),
          color = Color.White,
          modifier = Modifier.testTag("far_left_clock"),
        )
        if (amPmText.isNotEmpty()) {
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = amPmText,
            style = MaterialTheme.typography.titleMedium.copy(
              fontSize = TextUnit(dateSp, TextUnitType.Sp),
              fontWeight = FontWeight.SemiBold,
              shadow = textShadow,
            ),
            color = Color.White.copy(alpha = 0.80f),
            modifier = Modifier.padding(bottom = 10.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = fullDateText,
        style = MaterialTheme.typography.titleMedium.copy(
          fontSize = TextUnit(dateSp, TextUnitType.Sp),
          fontWeight = FontWeight.Medium,
          shadow = textShadow,
        ),
        color = Color.White.copy(alpha = 0.88f),
      )
    }
  }
}

@Composable
private fun HorizontalAppPage(
  apps: List<AppEntry>,
  profile: DisplayProfile,
  launchingComponentKey: String?,
  returningComponentKey: String?,
  onLaunchApp: (AppEntry, Rect?, RenderedSquircleIcon?) -> Unit,
  onLongPressApp: (AppEntry) -> Unit,
  onLongPressBlankSpace: () -> Unit,
  getCachedIcon: (String, Int) -> RenderedSquircleIcon?,
  loadIcon: suspend (AppEntry, Int) -> RenderedSquircleIcon?,
  modifier: Modifier = Modifier,
) {
  val rows = remember(apps, profile.columns) {
    apps.chunked(profile.columns)
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(
      space = profile.gridVerticalSpacingDp,
      alignment = Alignment.CenterVertically,
    ),
    modifier = modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        detectTapGestures(onLongPress = { onLongPressBlankSpace() })
      }
      .padding(horizontal = profile.horizontalPaddingDp),
  ) {
    for (rowApps in rows) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(profile.gridHorizontalSpacingDp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
      ) {
        for (colIndex in 0 until profile.columns) {
          val entry = rowApps.getOrNull(colIndex)
          if (entry != null) {
            key(entry.componentKey) {
              SquircleAppItem(
                entry = entry,
                iconSizeDp = profile.iconSizeDp,
                iconRenderPx = profile.iconRenderPx,
                labelFontSp = profile.labelFontSp,
                labelLineHeightSp = profile.labelLineHeightSp,
                labelMaxLines = profile.labelMaxLines,
                showLabel = true,
                isLaunchingTarget = entry.componentKey == launchingComponentKey,
                isReturningTarget = entry.componentKey == returningComponentKey,
                getCachedIcon = getCachedIcon,
                loadIcon = loadIcon,
                onClick = { bounds, asset -> onLaunchApp(entry, bounds, asset) },
                onLongClick = { onLongPressApp(entry) },
                modifier = Modifier.weight(1f),
              )
            }
          } else {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun ApplePageIndicator(
  pagerState: PagerState,
  pageCount: Int,
  onSelectPage: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.White.copy(alpha = 0.14f),
    shape = CircleShape,
    modifier = modifier.testTag("page_indicator"),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
      repeat(pageCount) { index ->
        val selected = pagerState.currentPage == index
        Box(
          modifier = Modifier
            .size(if (selected) 8.dp else 6.dp)
            .clip(CircleShape)
            .background(
              if (selected) Color.White else Color.White.copy(alpha = 0.38f)
            )
            .clickable { onSelectPage(index) },
        )
      }
    }
  }
}

@Composable
private fun AppleFrostedDock(
  dockApps: List<AppEntry>,
  dockGradientColors: List<Color>,
  profile: DisplayProfile,
  launchingComponentKey: String?,
  returningComponentKey: String?,
  onLaunchApp: (AppEntry, Rect?, RenderedSquircleIcon?) -> Unit,
  onLongPressApp: (AppEntry) -> Unit,
  getCachedIcon: (String, Int) -> RenderedSquircleIcon?,
  loadIcon: suspend (AppEntry, Int) -> RenderedSquircleIcon?,
  modifier: Modifier = Modifier,
) {
  val resolvedGradientColors = remember(dockGradientColors) {
    when {
      dockGradientColors.size >= 2 -> dockGradientColors.map { it.copy(alpha = 0.34f) }
      dockGradientColors.size == 1 -> {
        val c = dockGradientColors.first()
        listOf(c.copy(alpha = 0.36f), lerpColor(c, Color.White, 0.25f).copy(alpha = 0.28f))
      }
      else -> listOf(
        Color.White.copy(alpha = 0.20f),
        Color.White.copy(alpha = 0.14f),
      )
    }
  }

  val borderColors = remember(dockGradientColors) {
    when {
      dockGradientColors.size >= 2 -> dockGradientColors.map { lerpColor(it, Color.White, 0.45f).copy(alpha = 0.46f) }
      dockGradientColors.size == 1 -> {
        val c = lerpColor(dockGradientColors.first(), Color.White, 0.45f).copy(alpha = 0.46f)
        listOf(c, Color.White.copy(alpha = 0.24f))
      }
      else -> listOf(
        Color.White.copy(alpha = 0.34f),
        Color.White.copy(alpha = 0.16f),
      )
    }
  }

  val dockTintBrush = remember(resolvedGradientColors) {
    Brush.horizontalGradient(colors = resolvedGradientColors)
  }
  val dockGlassSheen = remember {
    Brush.verticalGradient(
      colors = listOf(
        Color.White.copy(alpha = 0.14f),
        Color(0x660B101B),
      )
    )
  }
  val dockBorderBrush = remember(borderColors) {
    Brush.horizontalGradient(colors = borderColors)
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(30.dp))
      .background(Color(0x880C121E), RoundedCornerShape(30.dp))
      .background(dockTintBrush, RoundedCornerShape(30.dp))
      .background(dockGlassSheen, RoundedCornerShape(30.dp))
      .border(0.85.dp, dockBorderBrush, RoundedCornerShape(30.dp))
      .testTag("apple_frosted_dock"),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
      dockApps.forEach { entry ->
        key(entry.componentKey) {
          SquircleAppItem(
            entry = entry,
            iconSizeDp = profile.dockIconSizeDp,
            iconRenderPx = profile.iconRenderPx,
            labelFontSp = profile.labelFontSp,
            labelLineHeightSp = profile.labelLineHeightSp,
            labelMaxLines = 1,
            showLabel = false,
            isLaunchingTarget = entry.componentKey == launchingComponentKey,
            isReturningTarget = entry.componentKey == returningComponentKey,
            getCachedIcon = getCachedIcon,
            loadIcon = loadIcon,
            onClick = { bounds, asset -> onLaunchApp(entry, bounds, asset) },
            onLongClick = { onLongPressApp(entry) },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun AppQuickActionsSheet(
  entry: AppEntry,
  isPinnedToDock: Boolean,
  iconRenderPx: Int,
  getCachedIcon: (String, Int) -> RenderedSquircleIcon?,
  loadIcon: suspend (AppEntry, Int) -> RenderedSquircleIcon?,
  onLaunch: () -> Unit,
  onTogglePinDock: () -> Unit,
  onAppInfo: () -> Unit,
) {
  var asset by remember(entry.componentKey, iconRenderPx) {
    mutableStateOf(getCachedIcon(entry.componentKey, iconRenderPx))
  }
  LaunchedEffect(entry.componentKey, iconRenderPx) {
    if (asset == null) {
      asset = loadIcon(entry, iconRenderPx)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 24.dp)
      .padding(bottom = 24.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      SquircleAppBadge(
        label = entry.label,
        bitmap = asset?.bitmap,
        sizeDp = 60.dp,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entry.label,
          style = MaterialTheme.typography.headlineMedium,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = entry.packageName,
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.6f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White.copy(alpha = 0.08f)),
    ) {
      QuickActionRow(
        icon = Icons.AutoMirrored.Rounded.OpenInNew,
        label = "Open ${entry.label}",
        tint = Color.White,
        onClick = onLaunch,
        testTag = "action_open_app",
      )

      QuickActionRow(
        icon = Icons.Rounded.PushPin,
        label = if (isPinnedToDock) {
          stringResource(id = R.string.remove_from_dock)
        } else {
          stringResource(id = R.string.pin_to_dock)
        },
        tint = if (isPinnedToDock) Color(0xFF22D3EE) else Color.White.copy(alpha = 0.92f),
        onClick = onTogglePinDock,
        testTag = "action_toggle_pin_dock",
      )

      QuickActionRow(
        icon = Icons.Rounded.Info,
        label = stringResource(id = R.string.app_info),
        tint = Color.White.copy(alpha = 0.9f),
        onClick = onAppInfo,
        testTag = "action_app_info",
      )
    }
  }
}

@Composable
private fun QuickActionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tint: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag(testTag),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(22.dp),
    )
    Spacer(modifier = Modifier.width(14.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.titleMedium,
      color = tint,
    )
  }
}

@Composable
private fun rememberLiveClockMillis(): Long {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var timeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

  DisposableEffect(context, lifecycleOwner) {
    val timeReceiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        timeMillis = System.currentTimeMillis()
      }
    }
    val filter = IntentFilter().apply {
      addAction(Intent.ACTION_TIME_TICK)
      addAction(Intent.ACTION_TIME_CHANGED)
      addAction(Intent.ACTION_TIMEZONE_CHANGED)
    }
    context.registerReceiver(timeReceiver, filter)

    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        timeMillis = System.currentTimeMillis()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      runCatching { context.unregisterReceiver(timeReceiver) }
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  return timeMillis
}
