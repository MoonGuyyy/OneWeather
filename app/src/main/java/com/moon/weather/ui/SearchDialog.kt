package com.moon.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moon.weather.data.GeocodingResult
import com.moon.weather.data.WeatherLocation

@Composable
fun SearchDialog(
    searchResults: List<GeocodingResult>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onLocationSelected: (WeatherLocation) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        onQueryChange(query)
        focusRequester.requestFocus()
    }

    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .weatherGlass(RectangleShape, Color.Black.copy(alpha = 0.55f))
            .pressHaptic().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                // block clicks from passing through
            }
    ) {
            WeatherContentFrame {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .padding(20.dp)
            ) {
                // Search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp) // Fixed height
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 20.dp),

                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onQueryChange(it)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "Search for a city...",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                onQueryChange("")
                            },
                            modifier = Modifier.pressHaptic().size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        TextButton(modifier = Modifier.pressHaptic(), onClick = onDismiss) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Results
                AnimatedVisibility(visible = isSearching) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(24.dp)
                    )
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(searchResults) { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .pressHaptic().clickable {
                                    onLocationSelected(
                                        WeatherLocation(
                                            name = result.name,
                                            lat = result.latitude,
                                            lon = result.longitude,
                                            country = result.country ?: ""
                                        )
                                    )
                                }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val subtitle = listOfNotNull(result.admin1, result.country).joinToString(", ")
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
    }
}
