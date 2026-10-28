package com.tclstreamer.launcher

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private object Noir {
    val Ink = Color(0xFF0B0B0C)
    val Surface = Color(0xFF16161A)
    val SurfaceHigh = Color(0xFF1F1F24)
    val Ivory = Color(0xFFF2EDE4)
    val Muted = Color(0xFF8C877E)
    val Gold = Color(0xFFC9A86A)

    val Serif = FontFamily.Serif
    val Sans = FontFamily.SansSerif

    val TileShape = RoundedCornerShape(6.dp)
    val FocusScale = 1.06f
}

@Composable
fun HomeScreen(content: HomeContent?, onLaunch: (Intent) -> Unit) {
    val heroFocus = remember { FocusRequester() }
    val firstTileFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Noir.Ink)
            .verticalScroll(rememberScrollState())
            // TV-safe margins so nothing falls into overscan.
            .padding(horizontal = 56.dp, vertical = 32.dp),
    ) {
        Header(onLaunch)
        Spacer(Modifier.height(28.dp))

        if (content == null) return@Column

        content.hero?.let { hero ->
            HeroCard(hero, Modifier.focusRequester(heroFocus), onLaunch)
            Spacer(Modifier.height(32.dp))
        }
        if (content.streaming.isNotEmpty()) {
            Shelf("Streaming", content.streaming, onLaunch)
            Spacer(Modifier.height(24.dp))
        }
        if (content.others.isNotEmpty()) {
            Shelf(
                "Apps",
                content.others,
                onLaunch,
                firstItemModifier = Modifier.focusRequester(firstTileFocus),
            )
        }

        // Land on the hero (or the first app) when the screen first fills in.
        var focused by remember { mutableStateOf(false) }
        LaunchedEffect(content) {
            if (focused) return@LaunchedEffect
            focused = runCatching {
                if (content.hero != null) heroFocus.requestFocus() else firstTileFocus.requestFocus()
            }.isSuccess
        }
    }
}

@Composable
private fun Header(onLaunch: (Intent) -> Unit) {
    val context = LocalContext.current
    val now by produceMinuteTicker()
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                now.format(DateTimeFormatter.ofPattern(timePattern)),
                style = TextStyle(
                    color = Noir.Ivory,
                    fontFamily = Noir.Serif,
                    fontWeight = FontWeight.Light,
                    fontSize = 52.sp,
                ),
            )
            BasicText(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                style = TextStyle(
                    color = Noir.Muted,
                    fontFamily = Noir.Sans,
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            BasicText(
                "N O I R",
                style = TextStyle(color = Noir.Gold, fontFamily = Noir.Serif, fontSize = 14.sp),
            )
            Spacer(Modifier.height(14.dp))
            TextPill("Settings") { onLaunch(Intent(Settings.ACTION_SETTINGS)) }
        }
    }
}

@Composable
private fun produceMinuteTicker() = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay((60 - value.second) * 1000L)
    }
}

@Composable
private fun HeroCard(app: TvApp, modifier: Modifier, onLaunch: (Intent) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) Noir.Gold else Color.Transparent, tween(180))
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, tween(180))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Noir.TileShape)
            .background(Brush.horizontalGradient(listOf(Noir.SurfaceHigh, Noir.Ink)))
            .border(1.5.dp, border, Noir.TileShape)
            .clickable(interactionSource = interaction, indication = null) { onLaunch(app.launchIntent) }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(app, Modifier.width(213.dp).height(120.dp))
        Spacer(Modifier.width(32.dp))
        Column {
            BasicText(
                "NOW SHOWING",
                style = TextStyle(color = Noir.Gold, fontSize = 11.sp, letterSpacing = 3.sp),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                app.label,
                style = TextStyle(color = Noir.Ivory, fontFamily = Noir.Serif, fontSize = 34.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                if (focused) "Press OK to start watching" else " ",
                style = TextStyle(color = Noir.Muted, fontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun Shelf(
    title: String,
    apps: List<TvApp>,
    onLaunch: (Intent) -> Unit,
    firstItemModifier: Modifier = Modifier,
) {
    BasicText(
        title.uppercase(),
        style = TextStyle(color = Noir.Muted, fontSize = 12.sp, letterSpacing = 3.sp),
    )
    Spacer(Modifier.height(12.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        // Room for the focus scale so the gold edge is never clipped.
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppTile(
                app,
                if (app === apps.first()) firstItemModifier else Modifier,
                onLaunch,
            )
        }
    }
}

@Composable
private fun AppTile(app: TvApp, modifier: Modifier, onLaunch: (Intent) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) Noir.FocusScale else 1f, tween(160))
    val border by animateColorAsState(if (focused) Noir.Gold else Color.Transparent, tween(160))
    val labelColor by animateColorAsState(if (focused) Noir.Ivory else Color.Transparent, tween(160))

    Column(modifier = Modifier.width(160.dp)) {
        Box(
            modifier = modifier
                .size(width = 160.dp, height = 90.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(Noir.TileShape)
                .border(1.5.dp, border, Noir.TileShape)
                .clickable(interactionSource = interaction, indication = null) { onLaunch(app.launchIntent) },
        ) {
            Artwork(app, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        BasicText(
            app.label,
            style = TextStyle(color = labelColor, fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The app's banner, or its icon and name on a quiet card when it has no banner. */
@Composable
private fun Artwork(app: TvApp, modifier: Modifier) {
    val banner = app.banner
    if (banner != null) {
        Image(
            bitmap = banner,
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(Noir.TileShape),
        )
        return
    }
    Row(
        modifier = modifier.clip(Noir.TileShape).background(Noir.Surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.icon?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
        }
        BasicText(
            app.label,
            style = TextStyle(color = Noir.Ivory, fontSize = 13.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextPill(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(if (focused) Noir.Ivory else Color.Transparent, tween(160))
    val text by animateColorAsState(if (focused) Noir.Ink else Noir.Muted, tween(160))
    val shape = RoundedCornerShape(50)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, Noir.Muted.copy(alpha = 0.4f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        BasicText(label, style = TextStyle(color = text, fontSize = 12.sp, letterSpacing = 1.sp))
    }
}
