package com.moon.weather.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moon.weather.data.WeatherLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun WeatherLocation.reorderKey() = "$lat:$lon:$name"

/** Drag pixels are read only by the row's layer; the list recomposes only when the order changes. */
private class LocationDragState(val list: LazyListState, initial: List<WeatherLocation>, val leadingItems: Int = 0) {
    var locations by mutableStateOf(initial)
    var activeKey by mutableStateOf<String?>(null)
    var dragging by mutableStateOf(false)
    private var draggedTop by mutableFloatStateOf(0f)
    private var dropOffset by mutableFloatStateOf(0f)
    private var settling by mutableStateOf(false)
    private var draggedHeight = 0
    private var pointerY = 0f
    private var originalOrder = initial
    private var settleJob: Job? = null

    fun start(point: Offset) {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull {
            point.y >= it.offset && point.y < it.offset + it.size
        } ?: return
        if (item.index < leadingItems || item.index >= leadingItems + locations.size) return
        settleJob?.cancel()
        activeKey = item.key as String
        originalOrder = locations
        draggedTop = item.offset.toFloat()
        draggedHeight = item.size
        pointerY = point.y
        settling = false
        dragging = true
    }

    fun drag(delta: Float) {
        if (!dragging) return
        draggedTop += delta
        pointerY += delta
    }

    fun translation(key: String): Float {
        if (activeKey != key) return 0f
        if (settling) return dropOffset
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return draggedTop - item.offset
    }

    suspend fun frame(edge: Float, maximumSpeed: Float, seconds: Float) {
        if (!dragging) return
        val layout = list.layoutInfo
        val speed = when {
            pointerY < layout.viewportStartOffset + edge ->
                -maximumSpeed * ((layout.viewportStartOffset + edge - pointerY) / edge).coerceIn(0f, 1f)
            pointerY > layout.viewportEndOffset - edge ->
                maximumSpeed * ((pointerY - layout.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
        if ((speed < 0f && list.canScrollBackward) || (speed > 0f && list.canScrollForward)) {
            list.scrollBy(speed * seconds)
        }
        val items = list.layoutInfo.visibleItemsInfo
        // A swap awaits measurement before another one: never use offsets from an older order.
        if (items.filter { it.index in leadingItems until leadingItems + locations.size }
                .any { locations.getOrNull(it.index - leadingItems)?.reorderKey() != it.key }) return
        val current = items.firstOrNull { it.key == activeKey } ?: return
        val center = draggedTop + draggedHeight / 2f
        val target = if (center > current.offset + current.size / 2f) {
            items.lastOrNull { it.index > current.index && it.index < leadingItems + locations.size && center > it.offset + it.size / 2f }
        } else {
            items.firstOrNull { it.index >= leadingItems && it.index < current.index && center < it.offset + it.size / 2f }
        } ?: return
        val first = list.firstVisibleItemIndex
        val scroll = list.firstVisibleItemScrollOffset
        locations = locations.toMutableList().apply { add(target.index - leadingItems, removeAt(current.index - leadingItems)) }
        // Keep the viewport still when the dragged city was its first visible key.
        if (current.index == first || target.index == first) list.scrollToItem(first, scroll)
    }

    fun finish(scope: CoroutineScope, cancelled: Boolean, commit: (List<WeatherLocation>) -> Unit) {
        if (!dragging) return
        dragging = false
        if (cancelled) locations = originalOrder
        else if (locations != originalOrder) commit(locations)
        settleJob = scope.launch {
            withFrameNanos { }
            dropOffset = translation(activeKey ?: return@launch)
            settling = true
            animate(dropOffset, 0f, animationSpec = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            )) { value, _ -> dropOffset = value }
            activeKey = null
            settling = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReorderableLocationsList(
    locations: List<WeatherLocation>,
    onLocationClick: (WeatherLocation) -> Unit,
    onDeleteLocation: (WeatherLocation) -> Unit,
    onReorderLocations: (List<WeatherLocation>) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    cardContent: (@Composable (WeatherLocation, Boolean) -> Unit)? = null
) {
    val pickup = hapticFeedbackAction {}
    val list = rememberLazyListState()
    val leadingItems = if (header == null) 0 else 1
    val drag = remember(list, leadingItems) { LocationDragState(list, locations, leadingItems) }
    val scope = rememberCoroutineScope()
    val commit by rememberUpdatedState(onReorderLocations)
    val density = LocalDensity.current
    val edge = with(density) { 48.dp.toPx() }
    val maximumSpeed = with(density) { 600.dp.toPx() }
    LaunchedEffect(locations, drag.activeKey) {
        if (drag.activeKey == null) drag.locations = locations
    }
    LaunchedEffect(drag.dragging) {
        var previous = withFrameNanos { it }
        while (drag.dragging) {
            val now = withFrameNanos { it }
            drag.frame(edge, maximumSpeed, ((now - previous) / 1_000_000_000f).coerceAtMost(.032f))
            previous = now
        }
    }
    LazyColumn(
        state = list,
        contentPadding = if (cardContent != null) PaddingValues(horizontal = 12.dp, vertical = 12.dp) else PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(if (cardContent != null) 16.dp else 0.dp),
        userScrollEnabled = !drag.dragging,
        modifier = modifier.testTag("locations-list").pointerInput(drag) {
            // The handler stays on the list, even when a city changes index or leaves the viewport.
            detectDragGesturesAfterLongPress(
                onDragStart = { point -> drag.start(point); if (drag.dragging) pickup() },
                onDrag = { change, amount -> change.consume(); drag.drag(amount.y) },
                onDragEnd = { drag.finish(scope, false, commit) },
                onDragCancel = { drag.finish(scope, true, commit) }
            )
        }
    ) {
        if (header != null) item(key = "locations-header") { header() }
        items(drag.locations, key = { it.reorderKey() }) { location ->
            val key = location.reorderKey()
            val lifted = drag.activeKey == key
            if (cardContent != null) {
                LocationSwipeToDelete(key, enabled = !lifted,
                    onDelete = { onDeleteLocation(location) }, modifier = Modifier
                    .then(if (lifted) Modifier else Modifier.animateItemPlacement(spring(
                        dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)))
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationY = drag.translation(key); shadowElevation = if (lifted) 8.dp.toPx() else 0f }
                    .fillMaxWidth().testTag("location-$key")
                ) { clickable ->
                    Box(Modifier.fillMaxWidth().pressHaptic(enabled = clickable)
                        .clickable(enabled = clickable,
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { onLocationClick(location) })) { cardContent(location, lifted) }
                }
                return@items
            }
            Row(
                modifier = Modifier
                    .then(if (lifted) Modifier else Modifier.animateItemPlacement(spring(
                        dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
                    )))
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationY = drag.translation(key) }
                    .fillMaxWidth()
                    .testTag("location-$key")
                    .background(if (lifted) Color.White.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(16.dp))
                    .pressHaptic().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {
                        onLocationClick(location)
                    })
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(location.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    if (location.country.isNotEmpty()) {
                        Text(location.country, color = Color.White.copy(alpha = .5f), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onDeleteLocation(location) }, modifier = Modifier.pressHaptic().size(32.dp)) {
                    Icon(Icons.Default.Delete, "Remove", tint = Color.White.copy(alpha = .4f), modifier = Modifier.size(18.dp))
                }
            }
        }
        if (footer != null) item(key = "locations-footer") { footer() }
    }
}
