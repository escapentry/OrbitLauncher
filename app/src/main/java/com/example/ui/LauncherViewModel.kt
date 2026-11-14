package com.example.ui

import android.app.Application
import android.net.Uri
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.RenderedSquircleIcon
import com.example.data.SquircleIconRenderer
import com.example.data.WallpaperPreset
import com.example.data.WallpaperRepository
import com.example.data.WallpaperState
import com.example.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface AppsLoadState {
  data object Loading : AppsLoadState

  data class Ready(
    val allApps: List<AppEntry>,
    val pages: List<List<AppEntry>>,
    val searchResults: List<AppEntry>,
    val dockApps: List<AppEntry>,
    val dockGradientColors: List<Color>,
  ) : AppsLoadState
}

enum class LaunchTransitionPhase {
  IDLE,
  LAUNCHING,
  LAUNCHED_WAITING_RETURN,
  CLOSING,
}

data class LaunchTransitionState(
  val phase: LaunchTransitionPhase = LaunchTransitionPhase.IDLE,
  val pendingEntry: AppEntry? = null,
  val componentKey: String? = null,
  val label: String = "",
  val originRect: Rect? = null,
  val iconAsset: RenderedSquircleIcon? = null,
  val splashColor: Color = Color(0xFF22D3EE),
  val secondarySplashColor: Color = Color(0xFF0F172A),
  val eventId: Int = 0,
)

data class LauncherUiState(
  val loadState: AppsLoadState = AppsLoadState.Loading,
  val wallpaperState: WallpaperState = WallpaperState(),
  val isWallpaperMenuOpen: Boolean = false,
  val searchQuery: String = "",
  val itemsPerPage: Int = 16,
  val dockSlotCount: Int = 4,
  val pinnedDockKeys: Set<String> = emptySet(),
  val selectedAppForActions: AppEntry? = null,
  val transitionState: LaunchTransitionState = LaunchTransitionState(),
)

sealed interface HomeNavigationAction {
  data object None : HomeNavigationAction
  data class ScrollToPage(val targetPage: Int) : HomeNavigationAction
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = AppRepository(application)
  private val wallpaperRepository = WallpaperRepository(application)
  private val iconRenderer = SquircleIconRenderer(application)

  private val _uiState = MutableStateFlow(LauncherUiState())
  val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

  private var cachedAllApps: List<AppEntry> = emptyList()
  private var cachedPinnedKeys: List<String> = emptyList()
  private var lastPrewarmedPx: Int = 0
  private var lastReturnedFromAppAtMs: Long = 0L
  private var hasPendingRecomputeAfterTransition: Boolean = false

  init {
    loadWallpaper()
    refreshApps()
    observePackageBroadcasts()
  }

  private fun loadWallpaper() {
    viewModelScope.launch {
      val state = wallpaperRepository.loadWallpaperState()
      _uiState.update { it.copy(wallpaperState = state) }
    }
  }

  private fun observePackageBroadcasts() {
    viewModelScope.launch {
      repository.observePackageChanges().collect { pkg ->
        if (!pkg.isNullOrBlank()) {
          iconRenderer.evictPackage(pkg)
        }
        refreshApps()
      }
    }
  }

  fun refreshApps() {
    viewModelScope.launch {
      cachedPinnedKeys = repository.loadPinnedDockKeys()
      val apps = repository.loadInstalledApps()
      cachedAllApps = apps
      val phase = _uiState.value.transitionState.phase
      if (phase == LaunchTransitionPhase.IDLE) {
        recomputeState()
      } else {
        hasPendingRecomputeAfterTransition = true
      }
      if (lastPrewarmedPx > 0) {
        prewarmIcons(apps, lastPrewarmedPx)
      }
    }
  }

  fun updateLayoutMetrics(itemsPerPage: Int, dockColumns: Int, targetIconRenderPx: Int) {
    val current = _uiState.value
    val layoutChanged = current.itemsPerPage != itemsPerPage || current.dockSlotCount != dockColumns
    if (layoutChanged) {
      _uiState.update {
        it.copy(
          itemsPerPage = itemsPerPage.coerceAtLeast(1),
          dockSlotCount = dockColumns.coerceAtLeast(1),
        )
      }
      if (cachedAllApps.isNotEmpty()) {
        recomputeState()
      }
    }
    if (targetIconRenderPx > 0 && targetIconRenderPx != lastPrewarmedPx) {
      lastPrewarmedPx = targetIconRenderPx
      if (cachedAllApps.isNotEmpty()) {
        prewarmIcons(cachedAllApps, targetIconRenderPx)
      }
    }
  }

  fun openWallpaperMenu() {
    if (_uiState.value.transitionState.phase != LaunchTransitionPhase.IDLE) return
    _uiState.update {
      it.copy(
        isWallpaperMenuOpen = true,
        selectedAppForActions = null,
      )
    }
  }

  fun dismissWallpaperMenu() {
    _uiState.update { it.copy(isWallpaperMenuOpen = false) }
  }

  fun selectWallpaperPreset(preset: WallpaperPreset) {
    _uiState.update { state ->
      state.copy(
        wallpaperState = state.wallpaperState.copy(activePreset = preset),
      )
    }
    viewModelScope.launch {
      wallpaperRepository.selectPreset(preset)
    }
  }

  fun saveCustomWallpaper(uri: Uri) {
    viewModelScope.launch {
      val bitmap = wallpaperRepository.saveCustomPhotoFromUri(uri)
      if (bitmap != null) {
        _uiState.update { state ->
          state.copy(
            wallpaperState = state.wallpaperState.copy(
              activePreset = WallpaperPreset.CUSTOM_PHOTO,
              customBitmap = bitmap,
              customVersion = state.wallpaperState.customVersion + 1,
            ),
            isWallpaperMenuOpen = false,
          )
        }
      }
    }
  }

  fun onSearchQueryChanged(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
    recomputeState()
  }

  fun clearSearch() {
    if (_uiState.value.searchQuery.isNotEmpty()) {
      _uiState.update { it.copy(searchQuery = "") }
      recomputeState()
    }
  }

  fun showAppActions(entry: AppEntry) {
    if (_uiState.value.transitionState.phase != LaunchTransitionPhase.IDLE) return
    _uiState.update {
      it.copy(
        selectedAppForActions = entry,
        isWallpaperMenuOpen = false,
      )
    }
  }

  fun dismissAppActions() {
    _uiState.update { it.copy(selectedAppForActions = null) }
  }

  fun launchApp(
    entry: AppEntry,
    hostView: View? = null,
    originBounds: Rect? = null,
    iconAsset: RenderedSquircleIcon? = null,
  ) {
    val activePhase = _uiState.value.transitionState.phase
    if (activePhase == LaunchTransitionPhase.LAUNCHING || activePhase == LaunchTransitionPhase.CLOSING) {
      return
    }

    if (originBounds != null && originBounds.width > 0f && originBounds.height > 0f) {
      // Stage 1: Show full circular splash expansion from the exact icon before executing startActivity
      _uiState.update { state ->
        state.copy(
          selectedAppForActions = null,
          isWallpaperMenuOpen = false,
          searchQuery = "",
          transitionState = LaunchTransitionState(
            phase = LaunchTransitionPhase.LAUNCHING,
            pendingEntry = entry,
            componentKey = entry.componentKey,
            label = entry.label,
            originRect = originBounds,
            iconAsset = iconAsset,
            splashColor = iconAsset?.dominantColor ?: Color(0xFF1F2937),
            secondarySplashColor = iconAsset?.secondaryColor ?: Color(0xFF0F172A),
            eventId = state.transitionState.eventId + 1,
          ),
        )
      }
    } else {
      // No icon origin bounds (e.g., search GO chip or sheet action): launch directly
      _uiState.update {
        it.copy(
          selectedAppForActions = null,
          isWallpaperMenuOpen = false,
          searchQuery = "",
        )
      }
      val launched = repository.launchApp(
        entry = entry,
        hostView = hostView,
        originBounds = null,
        afterSplashOverlay = false,
      )
      if (launched) {
        recordLaunchDeferred(entry.componentKey, recomputeImmediately = true)
      }
    }
  }

  /**
   * Called by the splash overlay once the launch ripple fills the screen, ensuring the launch
   * animation is visibly completed before executing the real activity launch.
   */
  fun commitPendingLaunch(eventId: Int, hostView: View? = null) {
    val tState = _uiState.value.transitionState
    if (tState.phase != LaunchTransitionPhase.LAUNCHING || tState.eventId != eventId) return
    val entry = tState.pendingEntry ?: run {
      clearLaunchTransition()
      return
    }

    _uiState.update { state ->
      if (state.transitionState.eventId == eventId) {
        state.copy(
          transitionState = state.transitionState.copy(
            phase = LaunchTransitionPhase.LAUNCHED_WAITING_RETURN,
          )
        )
      } else {
        state
      }
    }

    val launched = repository.launchApp(
      entry = entry,
      hostView = hostView,
      originBounds = tState.originRect,
      afterSplashOverlay = true,
    )
    if (launched) {
      // Defer recomputing dock order until after close animation completes so icon positions stay aligned
      recordLaunchDeferred(entry.componentKey, recomputeImmediately = false)
    } else {
      clearLaunchTransition()
    }
  }

  private fun recordLaunchDeferred(componentKey: String, recomputeImmediately: Boolean) {
    viewModelScope.launch {
      repository.recordAppLaunch(componentKey)
      cachedAllApps = cachedAllApps.map { item ->
        if (item.componentKey == componentKey) {
          item.copy(launchCount = item.launchCount + 1, lastLaunchedAt = System.currentTimeMillis())
        } else {
          item
        }
      }
      if (recomputeImmediately && _uiState.value.transitionState.phase == LaunchTransitionPhase.IDLE) {
        recomputeState()
      } else {
        hasPendingRecomputeAfterTransition = true
      }
    }
  }

  fun launchTopMatchIfSingleOrExact(): Boolean {
    val ready = _uiState.value.loadState as? AppsLoadState.Ready ?: return false
    val query = _uiState.value.searchQuery.trim().lowercase()
    if (query.isEmpty() || ready.searchResults.isEmpty()) return false

    val first = ready.searchResults.first()
    if (ready.searchResults.size == 1 || first.normalizedLabel == query) {
      launchApp(first)
      return true
    }
    return false
  }

  fun onReturnedToLauncher(): Boolean {
    val currentTransition = _uiState.value.transitionState
    val isPendingReturn =
      currentTransition.phase == LaunchTransitionPhase.LAUNCHED_WAITING_RETURN ||
        currentTransition.phase == LaunchTransitionPhase.LAUNCHING

    if (isPendingReturn && currentTransition.originRect != null) {
      lastReturnedFromAppAtMs = System.currentTimeMillis()
      _uiState.update { state ->
        state.copy(
          transitionState = currentTransition.copy(
            phase = LaunchTransitionPhase.CLOSING,
            eventId = currentTransition.eventId + 1,
          )
        )
      }
      return true
    }
    return false
  }

  fun handleHomeButtonPressed(currentPagerPage: Int, appPageCount: Int): HomeNavigationAction {
    if (onReturnedToLauncher()) {
      return HomeNavigationAction.None
    }
    val current = _uiState.value
    if (
      current.transitionState.phase == LaunchTransitionPhase.CLOSING ||
        (System.currentTimeMillis() - lastReturnedFromAppAtMs) < 400L
    ) {
      return HomeNavigationAction.None
    }

    if (current.searchQuery.isNotEmpty()) {
      clearSearch()
    }
    _uiState.update {
      it.copy(
        selectedAppForActions = null,
        isWallpaperMenuOpen = false,
      )
    }
    val target = resolveHomeTargetPage(currentPagerPage, appPageCount)
    return if (target != currentPagerPage) {
      HomeNavigationAction.ScrollToPage(target)
    } else {
      HomeNavigationAction.None
    }
  }

  fun clearLaunchTransition() {
    _uiState.update { state ->
      if (state.transitionState.phase == LaunchTransitionPhase.IDLE) {
        state
      } else {
        state.copy(transitionState = LaunchTransitionState())
      }
    }
    if (hasPendingRecomputeAfterTransition) {
      hasPendingRecomputeAfterTransition = false
      if (cachedAllApps.isNotEmpty()) {
        recomputeState()
      }
    }
  }

  fun openAppInfo(entry: AppEntry) {
    _uiState.update { it.copy(selectedAppForActions = null) }
    repository.openAppInfo(entry.packageName)
  }

  fun togglePinToDock(entry: AppEntry) {
    val maxSlots = _uiState.value.dockSlotCount
    _uiState.update { it.copy(selectedAppForActions = null) }
    viewModelScope.launch {
      cachedPinnedKeys = repository.togglePinnedDockKey(entry.componentKey, maxSlots)
      if (cachedAllApps.isNotEmpty()) {
        recomputeState()
        if (lastPrewarmedPx > 0) {
          val targetPx = lastPrewarmedPx
          iconRenderer.loadOrRender(
            packageName = entry.packageName,
            activityName = entry.activityName,
            componentKey = entry.componentKey,
            targetSizePx = targetPx,
          )
          recomputeState()
        }
      }
    }
  }

  fun openDefaultHomeSettings() {
    _uiState.update { it.copy(isWallpaperMenuOpen = false) }
    repository.openDefaultHomeSettings()
  }

  fun getCachedIcon(componentKey: String, sizePx: Int): RenderedSquircleIcon? =
    iconRenderer.getCached(componentKey, sizePx)

  suspend fun loadIcon(entry: AppEntry, sizePx: Int): RenderedSquircleIcon? =
    iconRenderer.loadOrRender(entry.packageName, entry.activityName, entry.componentKey, sizePx)

  private fun recomputeState() {
    val state = _uiState.value
    val allApps = cachedAllApps
    val pinned = cachedPinnedKeys
    val dockApps = repository.buildSmartDock(allApps, state.dockSlotCount, pinned)
    val pages = chunkIntoHorizontalPages(allApps, state.itemsPerPage)
    val searchResults = filterAndRankApps(allApps, state.searchQuery)
    val dockColors = resolveDockColors(dockApps)

    _uiState.update {
      it.copy(
        pinnedDockKeys = pinned.toSet(),
        loadState = AppsLoadState.Ready(
          allApps = allApps,
          pages = pages,
          searchResults = searchResults,
          dockApps = dockApps,
          dockGradientColors = dockColors,
        ),
      )
    }
  }

  private fun resolveDockColors(dockApps: List<AppEntry>): List<Color> {
    if (dockApps.isEmpty() || lastPrewarmedPx <= 0) return emptyList()
    return dockApps.mapNotNull { entry ->
      iconRenderer.getCached(entry.componentKey, lastPrewarmedPx)?.dominantColor
    }
  }

  private fun prewarmIcons(apps: List<AppEntry>, targetPx: Int) {
    viewModelScope.launch(Dispatchers.Default) {
      val concurrency = Semaphore(4)
      val dockApps = repository.buildSmartDock(apps, _uiState.value.dockSlotCount, cachedPinnedKeys)
      for (dockEntry in dockApps) {
        iconRenderer.loadOrRender(
          packageName = dockEntry.packageName,
          activityName = dockEntry.activityName,
          componentKey = dockEntry.componentKey,
          targetSizePx = targetPx,
        )
      }
      if (_uiState.value.transitionState.phase == LaunchTransitionPhase.IDLE) {
        recomputeState()
      }

      val remaining = apps.filterNot { app -> dockApps.any { it.componentKey == app.componentKey } }
      for (entry in remaining) {
        launch {
          concurrency.withPermit {
            iconRenderer.loadOrRender(
              packageName = entry.packageName,
              activityName = entry.activityName,
              componentKey = entry.componentKey,
              targetSizePx = targetPx,
            )
          }
        }
      }
    }
  }

  companion object {
    fun chunkIntoHorizontalPages(apps: List<AppEntry>, itemsPerPage: Int): List<List<AppEntry>> {
      if (apps.isEmpty()) return emptyList()
      val pageSize = itemsPerPage.coerceAtLeast(1)
      return apps.chunked(pageSize)
    }

    fun filterAndRankApps(apps: List<AppEntry>, query: String): List<AppEntry> {
      if (query.isBlank()) return apps
      val q = query.trim().lowercase()

      val prefixMatches = ArrayList<AppEntry>()
      val wordMatches = ArrayList<AppEntry>()
      val acronymMatches = ArrayList<AppEntry>()
      val substringMatches = ArrayList<AppEntry>()

      for (app in apps) {
        val label = app.normalizedLabel
        when {
          label.startsWith(q) -> prefixMatches.add(app)
          label.contains(" $q") -> wordMatches.add(app)
          app.acronym.startsWith(q) && app.acronym.isNotEmpty() -> acronymMatches.add(app)
          label.contains(q) -> substringMatches.add(app)
        }
      }
      return prefixMatches + wordMatches + acronymMatches + substringMatches
    }

    fun resolveHomeTargetPage(currentPage: Int, appPageCount: Int): Int {
      if (appPageCount <= 0) return 0
      return when {
        currentPage > 1 -> 1
        currentPage == 1 -> 0
        else -> 1
      }
    }
  }
}
