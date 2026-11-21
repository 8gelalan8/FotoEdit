package com.example.fotoedit

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yalantis.ucrop.UCrop
import java.io.File
import kotlin.math.roundToInt

// Paleta inspirada en apps de edición oscuras: carbón + acento teal
private val Bg = Color(0xFF121212)
private val Surface = Color(0xFF1E1E1E)
private val Accent = Color(0xFF20D5C4)

class Overlay(val label: String) {
    var offset by mutableStateOf(Offset(200f, 300f))
}

enum class Tool(val icon: ImageVector) {
    Recortar(Icons.Filled.Crop),
    Rotar(Icons.Filled.RotateRight),
    Ajustar(Icons.Filled.Tune),
    Texto(Icons.Filled.TextFields),
    Stickers(Icons.Filled.EmojiEmotions)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface)
            ) { EditorScreen() }
        }
    }
}

@Composable
fun EditorScreen() {
    val ctx = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(0f) }   // -1..1
    var contrast by remember { mutableFloatStateOf(1f) }     // 0.5..1.5
    var tool by remember { mutableStateOf<Tool?>(null) }
    val overlays = remember { mutableStateListOf<Overlay>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        it?.let { u -> uri = u }
    }
    val cropper = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.let { UCrop.getOutput(it) }?.let { uri = it }
    }

    // Brillo y contraste en una sola matriz de color
    val t = (1 - contrast) * 128f + brightness * 255f
    val filter = ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    fun onTool(selected: Tool) {
        val src = uri ?: return
        when (selected) {
            Tool.Recortar -> {
                val dest = Uri.fromFile(File(ctx.cacheDir, "crop_${System.currentTimeMillis()}.jpg"))
                cropper.launch(UCrop.of(src, dest).getIntent(ctx))
            }
            Tool.Rotar -> rotation = (rotation + 90f) % 360f
            Tool.Texto -> overlays.add(Overlay("Texto"))
            else -> tool = if (tool == selected) null else selected
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        // Barra superior: cerrar + exportar
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { uri = null; overlays.clear(); rotation = 0f; brightness = 0f; contrast = 1f }) {
                Icon(Icons.Filled.Close, null, tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { /* TODO: exportar (ver README) */ }, enabled = uri != null) { Text("Exportar") }
        }

        // Vista previa
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (uri == null) {
                Button(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Elegir foto") }
            } else {
                AsyncImage(
                    model = uri, contentDescription = null, contentScale = ContentScale.Fit,
                    colorFilter = filter,
                    modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = rotation)
                )
                overlays.forEach { o ->
                    Text(
                        o.label, color = Color.White, fontSize = 32.sp,
                        modifier = Modifier
                            .offset { IntOffset(o.offset.x.roundToInt(), o.offset.y.roundToInt()) }
                            .pointerInput(o) { detectDragGestures { _, d -> o.offset += d } }
                    )
                }
            }
        }

        // Panel contextual (sliders / stickers)
        AnimatedVisibility(tool == Tool.Ajustar || tool == Tool.Stickers) {
            Column(Modifier.fillMaxWidth().background(Surface).padding(16.dp)) {
                if (tool == Tool.Ajustar) {
                    Text("Brillo", color = Color.White)
                    Slider(brightness, { brightness = it }, valueRange = -1f..1f)
                    Text("Contraste", color = Color.White)
                    Slider(contrast, { contrast = it }, valueRange = 0.5f..1.5f)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(listOf("😀", "😍", "🔥", "⭐", "❤️", "🎉", "👍", "🌈")) { e ->
                            Text(e, fontSize = 36.sp, modifier = Modifier.clickable { overlays.add(Overlay(e)) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { tool = null }) { Icon(Icons.Filled.Check, null, tint = Accent) }
                }
            }
        }

        // Barra de herramientas inferior
        LazyRow(
            Modifier.fillMaxWidth().background(Surface).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(Tool.entries) { t ->
                Column(
                    Modifier.clickable { onTool(t) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(t.icon, null, tint = if (tool == t) Accent else Color.White)
                    Text(t.name, color = if (tool == t) Accent else Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
