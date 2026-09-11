package dev.birdmachine.precipice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.builditcode.glass.BackdropFilter
import com.builditcode.glass.LocalBackdropLayerManager
import com.builditcode.glass.glassBorder
import com.builditcode.glass.layeredBackdropCapture
import com.builditcode.glass.layeredBackdropSource
import com.builditcode.glass.rememberBackdropManager
import kotlin.math.sin

private const val BackdropLayer = "precipice-ambient"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PrecipiceApp() }
    }
}

@Composable
private fun PrecipiceApp() {
    val backdropManager = rememberBackdropManager(
        defaultScaleFactor = 0.55f,
        defaultDebounceMs = 16L,
    )

    CompositionLocalProvider(LocalBackdropLayerManager provides backdropManager) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF060711)),
        ) {
            AmbientWorld(
                modifier = Modifier
                    .fillMaxSize()
                    .layeredBackdropSource(BackdropLayer),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(72.dp))

                BasicText(
                    text = "PRECIPICE",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                    ),
                )

                Spacer(Modifier.height(44.dp))
                PrecipiceGlassOrb()
                Spacer(Modifier.height(34.dp))

                BasicText(
                    text = "touch the lens",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )

                Spacer(Modifier.height(18.dp))

                BasicText(
                    text = "the side of the seam",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.28f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AmbientWorld(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "ambient-world")
    val t by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12_000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambient-time",
    )

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF03040A),
                    Color(0xFF0A0920),
                    Color(0xFF070A12),
                ),
            ),
        )

        val w = size.width
        val h = size.height
        val phase = t * 6.283185f

        fun glow(center: Offset, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                center = center,
                radius = radius,
            )
        }

        glow(
            center = Offset(
                x = w * (0.28f + 0.13f * sin(phase)),
                y = h * (0.31f + 0.06f * sin(phase * 1.7f)),
            ),
            radius = w * 0.62f,
            color = Color(0xFF19D3FF).copy(alpha = 0.32f),
        )
        glow(
            center = Offset(
                x = w * (0.74f + 0.10f * sin(phase * 1.31f + 1.2f)),
                y = h * (0.38f + 0.08f * sin(phase * 1.11f + 2.1f)),
            ),
            radius = w * 0.54f,
            color = Color(0xFFFF38D1).copy(alpha = 0.27f),
        )
        glow(
            center = Offset(
                x = w * (0.52f + 0.12f * sin(phase * 0.83f + 3.4f)),
                y = h * (0.57f + 0.05f * sin(phase * 1.23f + 0.4f)),
            ),
            radius = w * 0.50f,
            color = Color(0xFF9CFF52).copy(alpha = 0.20f),
        )
    }
}

@Composable
private fun PrecipiceGlassOrb() {
    var pressed by remember { mutableStateOf(false) }
    var awakened by remember { mutableStateOf(false) }

    val breath = rememberInfiniteTransition(label = "orb-breath")
    val breathScale by breath.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-breath-scale",
    )

    val pressX by animateFloatAsState(
        targetValue = if (pressed) 1.075f else 1f,
        animationSpec = spring(dampingRatio = 0.46f, stiffness = 420f),
        label = "orb-press-x",
    )
    val pressY by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = spring(dampingRatio = 0.46f, stiffness = 420f),
        label = "orb-press-y",
    )
    val awakenedScale by animateFloatAsState(
        targetValue = if (awakened) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 120f),
        label = "orb-awakened",
    )

    // v0.2.5 expresses its glass geometry as a corner radius rather than a Shape.
    // Half the 228dp diameter makes the AGSL lens itself circular; Compose still clips
    // the capture to CircleShape below.
    val glass = remember {
        BackdropFilter.Glass(
            blurRadiusIntensity = 1.6f,
            cornerRadiusDp = 114f,
            refraction = 0.24f,
            dispersion = 0.19f,
            edge = 0.30f,
            tint = Color.White.copy(alpha = 0.025f),
        )
    }

    Box(
        modifier = Modifier
            .size(228.dp)
            .graphicsLayer {
                scaleX = breathScale * pressX * awakenedScale
                scaleY = breathScale * pressY * awakenedScale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { awakened = !awakened },
                )
            }
            .glassBorder(
                shape = CircleShape,
                borderColor = Color.White.copy(alpha = 0.74f),
                borderWidth = 1.1.dp,
                gapSize = 0.12f,
                softness = 0.045f,
            )
            .layeredBackdropCapture(
                layerName = BackdropLayer,
                shape = CircleShape,
                filter = glass,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (awakened) 0.10f else 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.37f, size.height * 0.30f),
                    radius = size.minDimension * 0.68f,
                ),
            )
        }

        Box(
            modifier = Modifier
                .size(if (awakened) 22.dp else 14.dp)
                .background(
                    color = Color.White.copy(alpha = if (awakened) 0.42f else 0.20f),
                    shape = CircleShape,
                ),
        )
    }
}
