package com.moon.weather.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.moon.weather.utils.weatherArtworkShape
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.statusBars
import com.moon.weather.data.WeatherLocation
import com.moon.weather.utils.Temperature
import com.moon.weather.ui.theme.ClarityCity
import com.moon.weather.ui.components.DailyForecastCard
import com.moon.weather.ui.components.HourlyForecastCard
import com.moon.weather.ui.components.InsightCard
import com.moon.weather.ui.components.FrostedGlassCard
import com.moon.weather.ui.components.UVIndexHumidityRow
import com.moon.weather.ui.components.WindDewPointRow
import com.moon.weather.ui.components.PressureVisibilityRow
import com.moon.weather.ui.components.SunriseSunsetCard
import com.moon.weather.ui.components.WeatherRecoveryCard
import androidx.compose.ui.res.stringResource
import com.moon.weather.R
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PagerBackground(
    artwork: WeatherArtwork?,
    adjacentArtwork: WeatherArtwork?,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (artwork != null) {
            Image(
                bitmap = artwork.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (adjacentArtwork != null && adjacentArtwork !== artwork) {
            Image(
                bitmap = adjacentArtwork.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = kotlin.math.abs(pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                }
            )
        }
    }
}

/** Explicit scene order: static artwork first, atmospheric motion directly over it. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun WeatherBackgroundScene(
    artwork: WeatherArtwork?, adjacentArtwork: WeatherArtwork?,
    pagerState: androidx.compose.foundation.pager.PagerState,
    weatherCode: Int, isDay: Boolean, adjacentWeatherCode: Int, adjacentIsDay: Boolean,
    enabled: Boolean, modifier: Modifier = Modifier, timeOverrideSeconds: Float? = null,
    lightningSeed: Int? = null
) {
    Box(modifier.testTag("weather-background-scene")) {
        PagerBackground(artwork, adjacentArtwork, pagerState, Modifier.fillMaxSize())
        WeatherAtmosphere(weatherCode, isDay, Modifier.fillMaxSize(), adjacentWeatherCode, adjacentIsDay,
            blend = { kotlin.math.abs(pagerState.currentPageOffsetFraction) }, enabled = enabled,
            timeOverrideSeconds = timeOverrideSeconds, lightningSeed = lightningSeed)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TopContentFade(artwork: WeatherArtwork?, adjacentArtwork: WeatherArtwork?,
                           pagerState: androidx.compose.foundation.pager.PagerState,
                           modifier: Modifier = Modifier) {
    if (artwork == null) return
    val fadeMask = remember {
        Brush.verticalGradient(
            0f to Color.White,
            0.42f to Color.White,
            1f to Color.Transparent
        )
    }
    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        drawImage(
            image = artwork.background,
            dstSize = artwork.screenSize,
            filterQuality = FilterQuality.Medium
        )
        if (adjacentArtwork != null && adjacentArtwork !== artwork) {
            drawImage(image = adjacentArtwork.background, dstSize = adjacentArtwork.screenSize,
                filterQuality = FilterQuality.Medium,
                alpha = kotlin.math.abs(pagerState.currentPageOffsetFraction).coerceIn(0f, 1f))
        }
        drawRect(
            brush = fadeMask,
            blendMode = BlendMode.DstIn
        )
    }
}

internal fun createNavbarMaskBitmap(width: Int = 256, height: Int = 128): ImageBitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)

    val cornerU = 0.22f
    val sagMax = 0.24f
    val plateauStart = 0.58f

    for (y in 0 until height) {
        val v = y.toFloat() / (height - 1).coerceAtLeast(1)
        for (x in 0 until width) {
            val u = x.toFloat() / (width - 1).coerceAtLeast(1)

            // Top curve sag: curves down as u approaches 0 or 1
            val sag = when {
                u < cornerU -> {
                    val p = (cornerU - u) / cornerU
                    sagMax * (1f - sqrt((1f - p * p).coerceAtLeast(0f)))
                }
                u > (1f - cornerU) -> {
                    val p = (u - (1f - cornerU)) / cornerU
                    sagMax * (1f - sqrt((1f - p * p).coerceAtLeast(0f)))
                }
                else -> 0f
            }

            // Alpha from top curve down to bottom with smooth easing
            val alpha = if (v < sag) {
                0f
            } else {
                val t = ((v - sag) / (1f - sag).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                if (t >= plateauStart) {
                    1f
                } else {
                    val progress = (t / plateauStart).coerceIn(0f, 1f)
                    progress * progress * (3f - 2f * progress)
                }
            }

            val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
            pixels[y * width + x] = (a shl 24) or 0x00FFFFFF
        }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BottomBlurFade(
    artwork: WeatherArtwork?,
    adjacentArtwork: WeatherArtwork?,
    pagerState: androidx.compose.foundation.pager.PagerState,
    maskBitmap: ImageBitmap,
    modifier: Modifier = Modifier
) {
    if (artwork == null) return
    var position by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier = modifier
            .onGloballyPositioned { coordinates -> position = coordinates.positionInWindow() }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        drawImage(
            image = artwork.blurred,
            srcSize = IntSize(artwork.blurred.width, artwork.blurred.height),
            dstOffset = IntOffset(-position.x.roundToInt(), -position.y.roundToInt()),
            dstSize = artwork.screenSize,
            filterQuality = FilterQuality.Medium
        )
        if (adjacentArtwork != null && adjacentArtwork !== artwork) {
            drawImage(image = adjacentArtwork.blurred,
                dstOffset = IntOffset(-position.x.roundToInt(), -position.y.roundToInt()),
                dstSize = adjacentArtwork.screenSize, filterQuality = FilterQuality.Medium,
                alpha = kotlin.math.abs(pagerState.currentPageOffsetFraction).coerceIn(0f, 1f))
        }
        drawImage(
            image = maskBitmap,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            blendMode = BlendMode.DstIn,
            filterQuality = FilterQuality.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = viewModel(),
    widgetLocationLat: Double? = null,
    widgetLocationLon: Double? = null,
    widgetLocationName: String? = null,
    widgetUseCurrentLocation: Boolean = false,
    widgetNavigationId: Long = 0
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val locationsLoaded by viewModel.locationsLoaded.collectAsState()
    val isLocating by viewModel.isLocating.collectAsState()
    val error by viewModel.error.collectAsState()
    val activeLocations by viewModel.activeLocations.collectAsState()
    val weatherCache by viewModel.weatherCache.collectAsState()
    val loadingLocations by viewModel.loadingLocations.collectAsState()
    val weatherErrors by viewModel.weatherErrors.collectAsState()
    val savedLocations by viewModel.savedLocations.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val useCelsius by viewModel.useCelsius.collectAsState()
    val useMilitaryTime by viewModel.useMilitaryTime.collectAsState()
    val useMph by viewModel.useMph.collectAsState()
    val appearanceMode by viewModel.appearanceMode.collectAsState()

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { activeLocations.size.coerceAtLeast(1) })
    val currentLocation = activeLocations.getOrNull(pagerState.currentPage)
    val scope = rememberCoroutineScope()

    val scrollStates = remember { mutableStateMapOf<WeatherLocation, LazyGridState>() }
    val isScrolled by remember(activeLocations) {
        derivedStateOf {
            val currentLoc = activeLocations.getOrNull(pagerState.currentPage)
            val state = currentLoc?.let { scrollStates[it] }
            state != null && (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 4)
        }
    }
    val pillProgress by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "location_pill_progress"
    )

    var showLocations by rememberSaveable { mutableStateOf(false) }
    val gpsLocation by viewModel.gpsLocation.collectAsState()
    var waitingForInitialGps by rememberSaveable { mutableStateOf(gpsLocation == null && pagerState.currentPage == 0) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val pullRefresh = rememberWeatherPullRefreshState(
        key = currentLocation,
        canPull = {
            val scroll = currentLocation?.let { scrollStates[it] }
            !showLocations && !showSearch && !showSettings && !pagerState.isScrollInProgress &&
                scroll != null && scroll.firstVisibleItemIndex == 0 && scroll.firstVisibleItemScrollOffset == 0
        },
        onRefresh = { currentLocation?.let { viewModel.refreshWeatherForLocation(it) } }
    )

    var pendingScrollToLocation by remember { mutableStateOf<WeatherLocation?>(null) }

    LaunchedEffect(activeLocations, gpsLocation, waitingForInitialGps, pendingScrollToLocation) {
        if (waitingForInitialGps && gpsLocation != null && activeLocations.firstOrNull() == gpsLocation &&
            pendingScrollToLocation == null) {
            pagerState.scrollToPage(0)
            waitingForInitialGps = false
        }
    }

    BackHandler(enabled = showSettings || showSearch) {
        when {
            showSettings -> showSettings = false
            showSearch -> showSearch = false
        }
    }

    var handledWidgetNavigationId by rememberSaveable { mutableLongStateOf(-1L) }

    // Open requested cities by place identity after the saved-location flow updates.
    RequestedCityNavigation(activeLocations, pendingScrollToLocation, pagerState) { selected ->
        pendingScrollToLocation = null
        handledWidgetNavigationId = widgetNavigationId
        scope.launch { scrollStates[selected]?.scrollToItem(0) }
    }

    // If app opens from a widget tap, jump to the matching location page.
    LaunchedEffect(activeLocations, widgetNavigationId, widgetLocationLat, widgetLocationLon, widgetLocationName, widgetUseCurrentLocation) {
        if (handledWidgetNavigationId == widgetNavigationId || pendingScrollToLocation != null) return@LaunchedEffect
        if (widgetUseCurrentLocation) {
            viewModel.clearWidgetLocation()
            if (gpsLocation != null) {
                pagerState.scrollToPage(0)
                handledWidgetNavigationId = widgetNavigationId
            }
            return@LaunchedEffect
        }
        if (widgetLocationLat == null || widgetLocationLon == null) return@LaunchedEffect
        if (widgetLocationLat.isNaN() || widgetLocationLon.isNaN()) return@LaunchedEffect
        waitingForInitialGps = false

        val targetIndex = activeLocations.indexOfFirst { loc ->
            val latClose = kotlin.math.abs(loc.lat - widgetLocationLat) < 0.01
            val lonClose = kotlin.math.abs(loc.lon - widgetLocationLon) < 0.01
            val nameMatches = widgetLocationName?.let { loc.name == it } ?: false
            (latClose && lonClose) || nameMatches
        }

        if (targetIndex >= 0) {
            handledWidgetNavigationId = widgetNavigationId
            if (pagerState.currentPage != targetIndex) pagerState.animateScrollToPage(targetIndex)
        } else {
            viewModel.openWidgetLocation(WeatherLocation(
                widgetLocationName ?: "Widget location", widgetLocationLat, widgetLocationLon
            ))
        }
    }


    // Select the nearest city during the gesture and prepare both neighboring backgrounds.
    val backgroundPage by remember { derivedStateOf { pagerState.currentPage } }
    val adjacentPage by remember {
        derivedStateOf {
            val offset = pagerState.currentPageOffsetFraction
            pagerState.currentPage + when {
                offset > 0f -> 1
                offset < 0f -> -1
                else -> 0
            }
        }
    }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val artworkShape = weatherArtworkShape(screenSize.width, screenSize.height,
        LocalConfiguration.current.smallestScreenWidthDp >= 600)
    val backgroundResources = remember(activeLocations, weatherCache, artworkShape) {
        activeLocations.map { location ->
            val current = weatherCache[location]?.current
            com.moon.weather.utils.WeatherCodeMapper.getBackgroundRes(
                current?.weatherCode ?: 0, (current?.isDay ?: 1) == 1, artworkShape)
        }
    }
    val currentPageWeather = activeLocations.getOrNull(backgroundPage)?.let { weatherCache[it] }
    val isDay = (currentPageWeather?.current?.isDay ?: 1) == 1
    val adjacentIsDay = activeLocations.getOrNull(adjacentPage)
        ?.let { weatherCache[it]?.current?.isDay }?.let { it == 1 } ?: isDay
    val backgroundWeatherCode = currentPageWeather?.current?.weatherCode ?: 0
    val adjacentWeatherCode = activeLocations.getOrNull(adjacentPage)
        ?.let { weatherCache[it]?.current?.weatherCode } ?: backgroundWeatherCode
    val backgroundRes = com.moon.weather.utils.WeatherCodeMapper.getBackgroundRes(backgroundWeatherCode, isDay, artworkShape)
    val locationName = currentLocation?.name ?: "Locating..."
    var lastArtwork by remember { mutableStateOf<WeatherArtwork?>(null) }
    var failedArtwork by remember(screenSize) { mutableStateOf<Int?>(null) }
    val preparedArtwork = remember(screenSize) { mutableStateMapOf<Int, WeatherArtwork>() }
    val artwork = preparedArtwork[backgroundRes] ?: WeatherArtworkLoader.cached(backgroundRes, screenSize) ?: lastArtwork
    val currentArtworkReady = preparedArtwork[backgroundRes] != null ||
        WeatherArtworkLoader.cached(backgroundRes, screenSize) != null
    val adjacentRes = backgroundResources.getOrNull(adjacentPage)
    val adjacentArtwork = adjacentRes?.let { preparedArtwork[it] ?: WeatherArtworkLoader.cached(it, screenSize) }
    LaunchedEffect(backgroundRes, screenSize) {
        if (screenSize != IntSize.Zero) {
            try {
                val loaded = WeatherArtworkLoader.load(context.resources, backgroundRes, screenSize)
                if (loaded == null) failedArtwork = backgroundRes else { lastArtwork = loaded; failedArtwork = null }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failedArtwork = backgroundRes }
        }
    }
    val preloadResources = remember(backgroundResources, backgroundPage) {
        listOfNotNull(backgroundResources.getOrNull(backgroundPage),
            backgroundResources.getOrNull(backgroundPage - 1),
            backgroundResources.getOrNull(backgroundPage + 1)).distinct()
    }
    LaunchedEffect(preloadResources, screenSize) {
        preparedArtwork.keys.toList().filter { it !in preloadResources }.forEach(preparedArtwork::remove)
        if (screenSize != IntSize.Zero) {
            preloadResources.forEach { resource ->
                try {
                    WeatherArtworkLoader.load(context.resources, resource, screenSize)?.let { preparedArtwork[resource] = it }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { /* The current-background effect supplies a usable fallback. */ }
            }
        }
    }

    var firstScreenReady by remember { mutableStateOf(false) }
    val hasWidgetTarget = !widgetUseCurrentLocation && widgetLocationLat != null && widgetLocationLon != null &&
        !widgetLocationLat.isNaN() && !widgetLocationLon.isNaN()
    val widgetTargetPending = handledWidgetNavigationId != widgetNavigationId && hasWidgetTarget &&
        currentLocation?.let { location ->
            (kotlin.math.abs(location.lat - widgetLocationLat!!) >= .01 ||
                kotlin.math.abs(location.lon - widgetLocationLon!!) >= .01) &&
                location.name != widgetLocationName
        } != false
    val currentPageLoading = currentLocation in loadingLocations
    val currentPageError = weatherErrors[currentLocation] ?: error
    val readyToPresent = locationsLoaded && (!isLocating || hasWidgetTarget) && (
        (!widgetTargetPending && currentPageWeather != null && (currentArtworkReady || failedArtwork == backgroundRes)) ||
            (!currentPageLoading && !isLocating && (currentPageError != null || activeLocations.isEmpty()))
        )
    val latestReadyToPresent by rememberUpdatedState(readyToPresent)
    LaunchedEffect(readyToPresent) {
        if (readyToPresent && !firstScreenReady) {
            // Let the first weather layout render beneath the cover before revealing it.
            withFrameNanos { }
            if (latestReadyToPresent) firstScreenReady = true
        }
    }


    // Permission launcher
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.fetchCurrentLocationWeather()
        }
    }

    // Refresh on every foreground return, including a trip while the app was in Recents.
    OnWeatherResume {
        val hasLocation = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (hasLocation) viewModel.fetchCurrentLocationWeather()
    }

    // Request permission only when needed; saved cities work without it.
    LaunchedEffect(Unit) {
        val hasLocation = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!hasLocation) locationPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val glassCrossfade = remember(adjacentArtwork, adjacentIsDay, adjacentWeatherCode, pagerState) {
        GlassCrossfade(adjacentArtwork, weatherTileTint(adjacentIsDay,adjacentWeatherCode)) { kotlin.math.abs(pagerState.currentPageOffsetFraction) }
    }
    WeatherFoldProvider {
    Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalWeatherArtwork provides artwork, LocalWeatherGlassCrossfade provides glassCrossfade,
        LocalWeatherGlassTint provides weatherTileTint(isDay,backgroundWeatherCode)) {
    Box(Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(WeatherLaunchBlue).onSizeChanged { screenSize = it }
                .testTag(if (currentArtworkReady) "weather-artwork-ready" else "weather-artwork-loading")) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {

                    WeatherBackgroundScene(
                        artwork = artwork,
                        adjacentArtwork = adjacentArtwork,
                        pagerState = pagerState,
                        weatherCode = backgroundWeatherCode,
                        isDay = isDay,
                        adjacentWeatherCode = adjacentWeatherCode,
                        adjacentIsDay = adjacentIsDay,
                        enabled = currentPageWeather != null && !showLocations && !showSearch && !showSettings && firstScreenReady,
                        modifier = Modifier.fillMaxSize()
                    )


                    Box(modifier = Modifier.fillMaxSize()) {
                        // Content Pager
                        if (isLoading && activeLocations.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        } else if (activeLocations.isEmpty()) {
                            WeatherRecoveryCard(
                                title = stringResource(R.string.weather_city_needed_title),
                                message = error ?: stringResource(R.string.weather_city_needed_message),
                                actionLabel = stringResource(R.string.weather_search_city),
                                onAction = { showSearch = true }, showRetryIcon = false
                            )
                        } else {
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = pagerState,
                                key = { index -> weatherPageKey(index, activeLocations, gpsLocation) },
                                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                                    state = pagerState,
                                    snapAnimationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(pullRefresh.connection)
                            ) { page ->
                                val pageOrigin = remember(page) { mutableStateOf<LayoutCoordinates?>(null) }
                                Box(Modifier.fillMaxSize().graphicsLayer().onGloballyPositioned { coordinates ->
                                    if (pageOrigin.value !== coordinates) pageOrigin.value = coordinates
                                }) {
                                CompositionLocalProvider(LocalWeatherGlassOrigin provides pageOrigin.value) {
                                val loc = activeLocations.getOrNull(page)
                                val pageScrollState = if (loc != null) {
                                    val state = rememberSaveable(loc, saver = LazyGridState.Saver) { LazyGridState() }
                                    DisposableEffect(loc, state) {
                                        scrollStates[loc] = state
                                        onDispose {
                                            if (scrollStates[loc] == state) {
                                                scrollStates.remove(loc)
                                            }
                                        }
                                    }
                                    state
                                } else {
                                    rememberLazyGridState()
                                }
                                val hourlyDetailsState = rememberSaveable(loc) { mutableStateOf(false) }
                                val pageWeather = loc?.let { weatherCache[it] }
                                val pageError = weatherErrors[loc] ?: error
                                // Retain the friendly card during a retry, rather than replacing it with a blank spinner.
                                var lastPageError by remember(loc) { mutableStateOf<String?>(null) }
                                LaunchedEffect(pageError, pageWeather) {
                                    if (pageError != null) lastPageError = pageError
                                    else if (pageWeather != null) lastPageError = null
                                }
                                val recoveryMessage = pageError ?: lastPageError

                                if (pageWeather == null) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (loc in loadingLocations && recoveryMessage == null) {
                                            CircularProgressIndicator(color = Color.White)
                                        } else {
                                            WeatherRecoveryCard(
                                                title = stringResource(R.string.weather_unavailable_title),
                                                message = recoveryMessage ?: stringResource(R.string.weather_service_unavailable),
                                                actionLabel = stringResource(R.string.weather_retry),
                                                isWorking = loc in loadingLocations,
                                                onAction = { loc?.let { viewModel.fetchWeatherForLocation(it, forceRefresh = true) } }
                                            )
                                        }
                                    }
                                } else {
                                    val weatherCode = pageWeather.current?.weatherCode ?: 0
                                    val rawTemp = pageWeather.current?.temperature ?: 0.0
                                    val currentTemp = Temperature.value(rawTemp, useCelsius)
                                    val feelsLikeTemp = Temperature.value(
                                        pageWeather.current?.apparentTemperature ?: rawTemp, useCelsius)
                                    val description = com.moon.weather.utils.WeatherCodeMapper.getWeatherDescription(weatherCode)

                                    WeatherContentFrame(singlePane = false) { layout ->
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(layout.columns),
                                        state = pageScrollState,
                                        horizontalArrangement = Arrangement.spacedBy(layout.gap.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                                            .testTag("weather-grid-${layout.columns}")
                                    ) {
                                        item(key = "top", span = { GridItemSpan(maxLineSpan) }) {
                                            Spacer(Modifier.height(if (layout.height < 480f) 68.dp else 88.dp))
                                        }
                                        item(key = "current", span = { GridItemSpan(maxLineSpan) }) {
                                            val maxTemp = pageWeather.daily?.maxTemperatures?.getOrNull(0)
                                                ?.let { Temperature.value(it, useCelsius).toString() } ?: "--"
                                            val minTemp = pageWeather.daily?.minTemperatures?.getOrNull(0)
                                                ?.let { Temperature.value(it, useCelsius).toString() } ?: "--"

                                            WeatherCurrentConditions(currentTemp, description, maxTemp, minTemp,
                                                feelsLikeTemp, expanded = layout.columns == 2, gap = layout.gap.dp,
                                                bottomSpace = if (layout.height >= 480f) 72.dp else 12.dp)
                                        }
                                        val hourlyCard: @Composable () -> Unit = {
                                            HourlyForecastCard(
                                                hourlyData = pageWeather.hourly,
                                                currentTime = pageWeather.current?.time,
                                                useCelsius = useCelsius,
                                                useMilitaryTime = useMilitaryTime,
                                                modifier = Modifier.testTag("weather-hourly-card"),
                                                currentWeather = pageWeather.current,
                                                detailsState = hourlyDetailsState
                                            )
                                        }
                                        val dailyCard: @Composable () -> Unit = {
                                            DailyForecastCard(
                                                dailyData = pageWeather.daily,
                                                useCelsius = useCelsius,
                                                modifier = Modifier.testTag("weather-daily-card")
                                            )
                                        }
                                        val insightCard: @Composable () -> Unit = {
                                            InsightCard(weather = pageWeather, useCelsius = useCelsius, useMph = useMph)
                                        }
                                        val uvCard: @Composable () -> Unit = {
                                            UVIndexHumidityRow(pageWeather.current, Modifier.testTag("weather-uv-card"))
                                        }
                                        val windCard: @Composable () -> Unit = {
                                            WindDewPointRow(pageWeather.current, Modifier.testTag("weather-wind-card"), useMph = useMph)
                                        }
                                        val pressureCard: @Composable () -> Unit = {
                                            PressureVisibilityRow(pageWeather.current, Modifier.testTag("weather-pressure-card"))
                                        }
                                        val sunCard: @Composable () -> Unit = {
                                            SunriseSunsetCard(
                                                dailyWeather = pageWeather.daily,
                                                currentTime = pageWeather.current?.time,
                                                modifier = Modifier.testTag("weather-sun-card")
                                            )
                                        }
                                        if (layout.columns == 2) {
                                            item(key = "forecast-columns", span = { GridItemSpan(maxLineSpan) }) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(layout.gap.dp)) {
                                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                        hourlyCard()
                                                        insightCard()
                                                        windCard()
                                                        sunCard()
                                                    }
                                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                        dailyCard()
                                                        uvCard()
                                                        pressureCard()
                                                    }
                                                }
                                            }
                                        } else {
                                            item(key = "hourly") { hourlyCard() }
                                            item(key = "insight") { insightCard() }
                                            item(key = "daily") { dailyCard() }
                                            item(key = "uv") { uvCard() }
                                            item(key = "wind") { windCard() }
                                            item(key = "pressure") { pressureCard() }
                                            item(key = "sun") { sunCard() }
                                        }
                                    }
                                    }
                                }
                                }
                                }
                            }
                        }
                    }

                    TopContentFade(
                        artwork = artwork,
                        adjacentArtwork = adjacentArtwork,
                        pagerState = pagerState,
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(140.dp)
                    )

                }

                // Layer 2: Fixed Overlay UI (Location Pill, Bottom Navigation)
                // Only show if not searching to clean up the UI
                AnimatedVisibility(
                    visible = !showSearch,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Location Pill (Top Left)
                        WeatherContentFrame(singlePane = false) { layout ->
                        Box(
                            modifier = Modifier
                                .widthIn(max = if (layout.columns == 2) ((layout.width - layout.gap - 40f) / 2).dp else 640.dp)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                                .padding(top = 12.dp, start = 16.dp)
                        ) {
                            FrostedGlassCard(
                                cornerRadius = 50.dp,
                                alpha = pillProgress
                            ) {
                                val iconSize = (24f - (4f * pillProgress)).dp
                                val fontSize = (22f - (4f * pillProgress)).sp
                                val iconSpacing = (8f - (2f * pillProgress)).dp
                                val shadowAlpha = (1f - pillProgress) * 0.45f
                                val textShadow = if (shadowAlpha > 0.01f) {
                                    androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = shadowAlpha),
                                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                } else null

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .pressHaptic().clickable { showLocations = true }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    ShadowedLocationPin(shadowAlpha = shadowAlpha,
                                        modifier = Modifier.size(iconSize))
                                    Spacer(modifier = Modifier.width(iconSpacing))
                                    Text(
                                        text = locationName,
                                        color = Color.White,
                                        fontSize = fontSize,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                                    )
                                }
                            }
                        }
                        }

                        // Bottom Navigation (Bottom Center)
                        val navMaskBitmap = remember { createNavbarMaskBitmap() }
                        val navBackgroundColor = weatherNavigationColor(backgroundWeatherCode, isDay)
                        val adjacentNavColor = weatherNavigationColor(adjacentWeatherCode, adjacentIsDay)

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                        ) {
                            // Layer 1: Gradual blur of whatever is underneath, getting stronger as it goes lower
                            BottomBlurFade(
                                artwork = artwork,
                                adjacentArtwork = adjacentArtwork,
                                pagerState = pagerState,
                                maskBitmap = navMaskBitmap,
                                modifier = Modifier.matchParentSize()
                            )

                            // Layer 2: Smooth color fade and curved top, extending through square bottom corners.
                            Canvas(
                                modifier = Modifier.matchParentSize()
                            ) {
                                drawImage(
                                    image = navMaskBitmap,
                                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                    colorFilter = ColorFilter.tint(lerp(navBackgroundColor, adjacentNavColor,
                                        kotlin.math.abs(pagerState.currentPageOffsetFraction).coerceIn(0f, 1f))),
                                    filterQuality = FilterQuality.Medium
                                )
                            }

                            // Opaque pixels shield the forecast behind them; the upper fade stays interactive.
                            NavbarTouchShield(navMaskBitmap, Modifier.matchParentSize())

                            // Layer 3: Navigation buttons and page indicators
                            WeatherBottomNavigation(
                                pagerState = pagerState,
                                onMenuClick = { showLocations = true },
                                onSearchClick = { showSearch = true },
                                hasCurrentLocation = gpsLocation != null,
                                contentColor = lerp(weatherNavigationContentColor(backgroundWeatherCode, isDay),
                                    weatherNavigationContentColor(adjacentWeatherCode, adjacentIsDay),
                                    kotlin.math.abs(pagerState.currentPageOffsetFraction).coerceIn(0f, 1f))
                            )
                        }
                        WeatherRefreshIndicator(pullRefresh, Modifier.align(Alignment.TopCenter))
                    }
                }
            }

            LocationsTransition(visible = showLocations, backEnabled = !showSearch && !showSettings, onBack = { showLocations = false }) {
                LocationsScreen(
                    locations = savedLocations, gpsLocation = gpsLocation, weather = weatherCache,
                    useCelsius = useCelsius, useMilitaryTime = useMilitaryTime,
                    onLocationClick = {
                        waitingForInitialGps = false
                        handledWidgetNavigationId = widgetNavigationId
                        if (it == gpsLocation) scope.launch { pagerState.scrollToPage(0) }
                        else pendingScrollToLocation = it
                        showLocations = false
                    },
                    onDelete = { viewModel.removeLocation(it) },
                    onReorder = { viewModel.reorderLocations(it) },
                    onUseGps = { viewModel.fetchCurrentLocationWeather() },
                    onSearch = { showSearch = true }, onSettings = { showSettings = true },
                    onBack = { showLocations = false }
                )
            }
            if (showSearch) {
                    SearchDialog(
                        searchResults = searchResults,
                        isSearching = isSearching,
                        onQueryChange = { viewModel.searchCity(it) },
                        onLocationSelected = { loc ->
                            waitingForInitialGps = false
                            handledWidgetNavigationId = widgetNavigationId
                            viewModel.saveLocation(loc)
                            pendingScrollToLocation = loc
                            showSearch = false
                            showLocations = false
                        },

                        onDismiss = { showSearch = false }
                    )
            }

            // Settings Dialog
            val settingsAlpha by animateFloatAsState(
                targetValue = if (showSettings) 1f else 0f,
                animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
                label =  "settings_alpha"
            )
            if (showSettings || settingsAlpha > 0.01f) {
                    SettingsDialog(
                        useCelsius = useCelsius,
                        useMilitaryTime = useMilitaryTime,
                        appearanceMode = appearanceMode,
                        animatedAlpha = settingsAlpha,
                        onUseCelsiusChange = { viewModel.setUseCelsius(it) },
                        onUseMilitaryTimeChange = { viewModel.setUseMilitaryTime(it) },
                        useMph = useMph,
                        onUseMphChange = { viewModel.setUseMph(it) },
                        onAppearanceChange = { viewModel.setAppearanceMode(it) },
                        onDismiss = {
                            showSettings = false
                        }
                    )
            }
        }
    }
    }
    WeatherLaunchCover(ready = firstScreenReady)
    }
    }
}



@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WeatherBottomNavigation(
    pagerState: androidx.compose.foundation.pager.PagerState,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    contentColor: Color = Color.White,
    hasCurrentLocation: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val pageCount = pagerState.pageCount
    var firstVisiblePage by remember(pageCount, hasCurrentLocation) { mutableIntStateOf(0) }
    val visiblePages = remember(pageCount, pagerState.currentPage, firstVisiblePage) {
        navbarIndicatorWindow(pageCount, pagerState.currentPage, firstVisiblePage)
    }
    SideEffect { firstVisiblePage = visiblePages.firstOrNull()?.page ?: 0 }
    val dotCenters = remember(visiblePages,hasCurrentLocation) { mutableStateMapOf<Int, Float>() }
    val popup = LocalWeatherPopupLayout.current
    val indicatorOffset = popup?.let { it.pane.left + it.pane.width / 2 - it.windowWidth / 2 } ?: 0f

    Row(
        modifier = Modifier
            .testTag("weather-bottom-navigation")
            .fillMaxWidth()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .offset(y = 8.dp)
            .background(Color.Transparent)
            .padding(start = 20.dp, top = 45.dp, end = 20.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(modifier = Modifier.pressHaptic(), onClick = onMenuClick) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = contentColor)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(48.dp)
                .absoluteOffset(x = indicatorOffset.dp)
                .padding(horizontal = 12.dp)
                .pressHaptic().pointerInput(visiblePages,hasCurrentLocation) {
                    detectTapGestures { offset ->
                        if (dotCenters.isNotEmpty()) {
                            val closest = dotCenters.minByOrNull { kotlin.math.abs(it.value - offset.x) }?.key
                            if (closest != null && closest != pagerState.currentPage) {
                                scope.launch { pagerState.animateScrollToPage(closest) }
                            }
                        }
                    }
                }
        ) {
            for (indicator in visiblePages) key(indicator.page) {
                val i = indicator.page
                val isSelected = i == pagerState.currentPage
                val scale by animateFloatAsState(indicator.scale,tween(160),label = "navbar-indicator-scale")
                Box(
                    modifier = Modifier
                        .testTag("navbar-page-$i")
                        .semantics {
                            selected = isSelected
                            contentDescription = if (i == 0 && hasCurrentLocation) "Go to current location" else "Go to city ${i + 1}"
                            role = Role.Button
                            onClick {
                                if (i != pagerState.currentPage) {
                                    scope.launch { pagerState.animateScrollToPage(i) }
                                }
                                true
                            }
                        }
                        .onGloballyPositioned { coordinates ->
                            dotCenters[i] = coordinates.positionInParent().x + coordinates.size.width / 2f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                        if (i == 0 && hasCurrentLocation) {
                            GlassLocationIndicator(isSelected = isSelected, color = contentColor)
                        } else {
                            GlassDotIndicator(isSelected = isSelected, color = contentColor)
                        }
                    }
                }
            }
        }

        IconButton(modifier = Modifier.pressHaptic(), onClick = onSearchClick) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = contentColor)
        }
    }
}

@Composable
private fun GlassDotIndicator(isSelected: Boolean, color: Color = Color.White) {
    Box(Modifier.size(12.dp).clip(CircleShape)
        .background(Brush.radialGradient(listOf(
            color.copy(alpha = if (isSelected) .95f else .24f),
            color.copy(alpha = if (isSelected) .65f else .14f))))
        .drawWithCache {
            val rim = androidx.compose.ui.graphics.drawscope.Stroke(.8.dp.toPx())
            onDrawBehind {
                drawCircle(color.copy(alpha = .12f), style = rim)
                for (segment in 0 until 36) {
                    val t = segment / 35f
                    val glow = kotlin.math.sin(t * kotlin.math.PI).toFloat()
                    drawArc(color.copy(alpha = .12f + .38f * glow),
                        startAngle = 125f + t * 180f, sweepAngle = 5.5f,
                        useCenter = false, style = rim)
                }
            }
        })
}

@Composable
private fun GlassLocationIndicator(isSelected: Boolean, color: Color = Color.White) {
    Icon(Icons.Default.LocationOn, "Current location page",
        tint = color.copy(alpha = if (isSelected) 1f else .45f),
        modifier = Modifier.size(18.dp))
}
