package com.app.hamfilm.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.app.hamfilm.desktop.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Animated GIF sticker renderer (romantic movie-scene packs).
 *
 * Frames are decoded once per composition on the IO thread (javax.imageio
 * composites partial GIF frames internally) and ticked at the GIF's own frame
 * durations. Static PNG packs fall back to [Res.sticker] automatically, so
 * callers just render [StickerImage] and never care about the format.
 */
@Composable
fun StickerImage(fileName: String, modifier: Modifier) {
    val frames by produceState<List<Res.GifFrame>?>(initialValue = null, fileName) {
        value = withContext(Dispatchers.IO) { Res.gifFrames(fileName) }
    }
    val gif = frames
    if (gif != null && gif.size > 1) {
        AnimatedFrames(gif, modifier)
    } else {
        val static = remember(fileName) { Res.sticker(fileName) }
        if (static != null) {
            Image(bitmap = static, contentDescription = fileName, modifier = modifier)
        } else if (gif != null && gif.isNotEmpty()) {
            Image(bitmap = gif[0].bitmap, contentDescription = fileName, modifier = modifier)
        }
    }
}

@Composable
private fun AnimatedFrames(frames: List<Res.GifFrame>, modifier: Modifier) {
    var index by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        var i = 0
        while (true) {
            delay(frames[i].durationMs.coerceIn(30, 1200).toLong())
            i = (i + 1) % frames.size
            index = i
        }
    }
    val frame = frames[index % frames.size]
    Image(
        bitmap = frame.bitmap,
        contentDescription = null,
        modifier = modifier
    )
}
