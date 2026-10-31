package com.tclstreamer.launcher

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private object Home {
    val Background = Color(0xFF0E0E10)
    val Tile = Color(0xFF1C1C21)
    val Text = Color.White
    val TextSoft = Color(0x99FFFFFF)
    val Ivory = Color(0xFFF2EDE4)
    val Sans = FontFamily.SansSerif

    val TileShape = RoundedCornerShape(12.dp)
    val TileWidth = 148.dp
    val TileHeight = 83.dp
    val Gap = 26.dp
    val SideMargin = 56.dp
    /** One pixel of the scene art: 240×75 scene pixels fill the 960×300 dp shelf. */
    val ScenePixel = 4.dp
    val ShelfHeight = ScenePixel * ShelfArt.H
    /** How far the top row of apps rises into the bottom of the shelf. */
    val DockOverlap = 64.dp
    const val Columns = 5
    const val FocusScale = 1.12f
}

@Composable
fun HomeScreen(
    apps: List<TvApp>?,
    quickTiles: List<QuickTile>,
    ambience: Ambience,
    /** The temperature outside in °C, when the weather has loaded. */
    temperature: Int?,
    watching: List<WatchItem>,
    onOpen: (WatchItem) -> Unit,
    onLaunch: (Intent) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Home.Background)) {
        if (apps.isNullOrEmpty()) return@Box

        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val firstFocus = remember { FocusRequester() }
        // The top shelf follows focus to whichever app is selected.
        var shelfApp by remember { mutableStateOf(apps.first()) }
        if (shelfApp !in apps) shelfApp = apps.first()

        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            TopShelf(shelfApp, ambience)
            Column(
                modifier = Modifier
                    .offset(y = -Home.DockOverlap)
                    .padding(horizontal = Home.SideMargin),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                apps.chunked(Home.Columns).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Home.Gap)) {
                        row.forEach { app ->
                            AppTile(
                                app = app,
                                modifier = if (app === apps.first()) Modifier.focusRequester(firstFocus) else Modifier,
                                onFocused = {
                                    shelfApp = app
                                    if (rowIndex == 0) scope.launch { scroll.animateScrollTo(0) }
                                },
                                onLaunch = onLaunch,
                            )
                        }
                    }
                    if (rowIndex == 0 && watching.isNotEmpty()) WatchRow(watching, onOpen)
                }
                // Wi-Fi, inputs, the sleep timer and settings, since the stock home screen is off.
                quickTiles.chunked(Home.Columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Home.Gap)) {
                        row.forEach { tile -> QuickTileView(tile) }
                    }
                }
            }
        }

        Clock(temperature, Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = Home.SideMargin))

        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
}

/**
 * The focused app's own pixel-art scene, with the two friends busy at its activity under a sky
 * of the app's colour, fading into the background behind the grid.
 */
@Composable
private fun TopShelf(app: TvApp, ambience: Ambience) {
    Box(Modifier.fillMaxWidth().height(Home.ShelfHeight)) {
        Crossfade(targetState = app, animationSpec = tween(500), label = "scene") { shown ->
            ShelfScene(shown.activity, shown.hue, ambience, Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    // A light veil up top keeps the clock readable.
                    0f to Color.Black.copy(alpha = 0.3f),
                    0.2f to Color.Transparent,
                    0.62f to Color.Transparent,
                    0.85f to Home.Background.copy(alpha = 0.75f),
                    1f to Home.Background,
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.55f),
                    0.45f to Color.Transparent,
                )
            )
        )
        // The launcher's name, centred just above the row of apps.
        PixelWordmark(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Home.DockOverlap + 16.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = Home.SideMargin, bottom = Home.DockOverlap + 28.dp),
        ) {
            BasicText(
                app.label,
                style = TextStyle(
                    color = Home.Text,
                    fontFamily = Home.Sans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                "Press OK to open",
                style = TextStyle(color = Home.TextSoft, fontFamily = Home.Sans, fontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun Clock(temperature: Int?, modifier: Modifier) {
    val context = LocalContext.current
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay((60 - value.second) * 1000L)
        }
    }
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    BasicText(
        now.format(DateTimeFormatter.ofPattern(pattern)) + (temperature?.let { "   $it°" } ?: ""),
        modifier = modifier,
        style = TextStyle(
            color = Home.Text,
            fontFamily = Home.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
        ),
    )
}

@Composable
private fun AppTile(
    app: TvApp,
    modifier: Modifier,
    onFocused: () -> Unit,
    onLaunch: (Intent) -> Unit,
) {
    Tile(app.label, modifier, onFocused, onClick = { onLaunch(app.launchIntent) }) {
        Artwork(app, Modifier.fillMaxSize())
    }
}

/** Half-watched titles from Plex and Stremio, right under the streaming apps. */
@Composable
private fun WatchRow(items: List<WatchItem>, onOpen: (WatchItem) -> Unit) {
    Column {
        BasicText(
            "Continue watching",
            style = TextStyle(color = Home.TextSoft, fontFamily = Home.Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Home.Gap),
        ) {
            items.forEach { item ->
                Tile(item.title, Modifier, onFocused = {}, onClick = { onOpen(item) }) {
                    Box(Modifier.fillMaxSize().background(Home.Tile)) {
                        val image = item.image
                        if (image != null) {
                            Image(image, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            BasicText(
                                item.title,
                                modifier = Modifier.align(Alignment.Center).padding(8.dp),
                                style = TextStyle(color = Home.Text, fontFamily = Home.Sans, fontSize = 11.sp, textAlign = TextAlign.Center),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // How far through it you are.
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x55000000)))
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(item.progress.coerceIn(0.02f, 1f))
                                .height(3.dp)
                                .background(Home.Ivory)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTileView(tile: QuickTile) {
    Tile(tile.label, Modifier, onFocused = {}, onClick = tile.onClick, onLongClick = tile.onLongClick) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2A31), Home.Tile))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PixelIcon(tile.icon, 3.dp)
            Spacer(Modifier.height(6.dp))
            BasicText(
                tile.label,
                style = TextStyle(color = Home.Text, fontFamily = Home.Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            tile.detail?.let {
                BasicText(
                    it,
                    style = TextStyle(color = Home.TextSoft, fontFamily = Home.Sans, fontSize = 10.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PixelIcon(rows: List<String>, pixel: Dp) {
    Canvas(Modifier.size(pixel * rows[0].length, pixel * rows.size)) {
        val p = pixel.toPx()
        rows.forEachIndexed { y, line ->
            line.forEachIndexed { x, ch ->
                if (ch == '#') drawRect(Home.Ivory, Offset(x * p, y * p), Size(p, p))
            }
        }
    }
}

/** A tile that lifts and shows its name while focused. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    label: String,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) Home.FocusScale else 1f, tween(200))
    val labelColor by animateColorAsState(if (focused) Home.Text else Color.Transparent, tween(200))

    Column(
        modifier = Modifier.width(Home.TileWidth).zIndex(if (focused) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = modifier
                .size(Home.TileWidth, Home.TileHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (focused) 20.dp.toPx() else 0f
                    shape = Home.TileShape
                    clip = true
                }
                .onFocusChanged { if (it.isFocused) onFocused() }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
        ) {
            content()
            if (focused) {
                // The glassy highlight across the top of a lifted tile.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        BasicText(
            label,
            style = TextStyle(
                color = labelColor,
                fontFamily = Home.Sans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The app's banner, or its icon on a tinted card when it has no banner. */
@Composable
private fun Artwork(app: TvApp, modifier: Modifier) {
    val banner = app.banner
    if (banner != null) {
        Image(
            bitmap = banner,
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(app.tint, Home.Tile)))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        app.icon?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(6.dp))
        }
        BasicText(
            app.label,
            style = TextStyle(color = Home.Text, fontFamily = Home.Sans, fontSize = 11.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
