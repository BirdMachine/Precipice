package dev.birdmachine.precipice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.builditcode.glass.LocalBackdropLayerManager
import com.builditcode.glass.layeredBackdropSource
import com.builditcode.glass.rememberBackdropManager
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private lateinit var shell: ShellModel
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) shell.startListening() else shell.fail("Microphone permission declined. You can still type a message.")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shell = ViewModelProvider(this)[ShellModel::class.java]
        setContent { PrecipiceApp(shell, ::talk) { url ->
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                .onFailure { shell.cancelSignIn(); shell.fail("No browser could open this link.") }
        } }
    }
    private fun talk() {
        when (shell.state) {
            AvatarState.Listening -> shell.finishListening()
            AvatarState.Speaking, AvatarState.Thinking -> shell.stop()
            else -> if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) shell.startListening()
                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    override fun onStop() { shell.pauseAudio(); super.onStop() }
}

private val ink = Color(0xFFF2EFFF)
private val accent = Color(0xFFE4AAED)
private val smallText = TextStyle(color = ink, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)

@Composable
private fun PrecipiceApp(shell: ShellModel, talk: () -> Unit, openBrowser: (String) -> Unit) {
    var settings by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val backdrop = rememberBackdropManager(defaultScaleFactor = 0.45f, defaultDebounceMs = 32L)
    CompositionLocalProvider(LocalBackdropLayerManager provides backdrop) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF060711))) {
            AmbientWorld(Modifier.fillMaxSize().layeredBackdropSource("precipice-ambient"))
            // All interactive controls, including settings, stay above Kestrel's damaged lower third.
            val safeHeight = maxHeight * 0.66f
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().height(safeHeight).align(Alignment.TopCenter)
                .padding(top = 30.dp, start = 18.dp, end = 18.dp, bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("PRECIPICE", style = smallText.copy(letterSpacing = 3.sp, color = accent))
                    Pill(if (settings) "Back" else "Settings") { settings = !settings }
                }
                Spacer(Modifier.height(8.dp))
                if (settings) SettingsPanel(shell, openBrowser, Modifier.weight(1f))
                else {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(30.dp)).clickable(role = Role.Button, onClickLabel = "Talk or stop", onClick = talk)
                        .semantics { contentDescription = "${shell.state.name} avatar. Tap to talk or stop." }, contentAlignment = Alignment.Center) {
                        if (shell.showMaid) PaperDoll(shell.state, Modifier.fillMaxHeight().aspectRatio(2f / 3f))
                        else if (Build.VERSION.SDK_INT >= 33) PrecipiceGlassOrb(shell.state, talk)
                        else Canvas(Modifier.size(160.dp)) { drawCircle(accent.copy(alpha = 0.35f)) }
                    }
                    BasicText(shell.status, style = smallText.copy(color = if (shell.state == AvatarState.Error) Color(0xFFFFC1D5) else ink), maxLines = 3)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill(when(shell.state) { AvatarState.Listening -> "Finish"; AvatarState.Speaking, AvatarState.Thinking -> "Stop"; else -> "Talk" }, enabled = !shell.connecting, action = talk)
                        Pill(if (shell.showMaid) "Orb" else "Maid", action = shell::toggleAvatar)
                        Pill("Preview") { settings = true }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(text, { text = it }, textStyle = smallText,
                            modifier = Modifier.weight(1f).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(11.dp)
                                .semantics { contentDescription = "Type a message" }, singleLine = true,
                            decorationBox = { inner -> if (text.isEmpty()) BasicText("Type a message…", style = smallText.copy(color = ink.copy(alpha = 0.45f))); inner() })
                        Pill("Send", enabled = text.isNotBlank() && !shell.connecting) { shell.send(text); text = "" }
                    }
                    Spacer(Modifier.height(6.dp))
                    BasicText(if (shell.connected) "Using ChatGPT plan • ${shell.selectedModel.ifBlank { "choose a model" }}" else "Connect ChatGPT in settings • preview works offline", style = smallText.copy(fontSize = 10.sp, color = accent))
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(shell: ShellModel, openBrowser: (String) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("ChatGPT connection", style = smallText.copy(fontSize = 18.sp, color = accent))
        BasicText(if (shell.connected) shell.account else "Use your eligible ChatGPT plan in Precipice.", style = smallText)
        if (shell.connecting) Pill("Cancel sign-in", action = shell::cancelSignIn)
        else Pill(if (shell.connected) "Reconnect with ChatGPT" else "Continue with ChatGPT") { shell.signIn(openBrowser) }
        BasicText(shell.status, style = smallText, maxLines = 5)
        if (shell.connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Manage usage") { openBrowser("https://chatgpt.com/#settings/Usage") }
                Pill("Disconnect", action = shell::disconnect)
            }
            BasicText("Model", style = smallText.copy(color = accent))
            if (shell.models.isEmpty()) Pill("Refresh models", action = shell::refreshModels)
            shell.models.forEach { model -> Pill((if (shell.selectedModel == model.slug) "● " else "○ ") + model.label) { shell.selectModel(model.slug) } }
        }
        BasicText("Avatar expressions", style = smallText.copy(fontSize = 18.sp, color = accent))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Idle") { shell.preview(AvatarState.Idle) }
            Pill("Listen") { shell.preview(AvatarState.Listening) }
            Pill("Think") { shell.preview(AvatarState.Thinking) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Speak") { shell.preview(AvatarState.Speaking) }
            Pill("Error") { shell.preview(AvatarState.Error) }
            Pill("Stop", action = shell::stop)
        }
        BasicText("Phone voice speed", style = smallText.copy(color = accent))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1f, 1.2f, 1.5f, 2f).forEach { rate -> Pill((if (shell.ttsRate == rate) "● " else "") + "${rate}×") { shell.setRate(rate) } }
        }
        BasicText(shell.speechRoute, style = smallText.copy(fontSize = 11.sp))
        BasicText("This shell keeps its own conversation. Your existing ChatGPT chats and memories are not imported.", style = smallText.copy(fontSize = 11.sp, color = ink.copy(alpha = 0.7f)))
        Pill("New conversation", action = shell::clearConversation)
        if (shell.transcript.isNotBlank()) BasicText("You: ${shell.transcript}", style = smallText)
        if (shell.response.isNotBlank()) BasicText("Reply: ${shell.response}", style = smallText)
        BasicText("Precipice 0.2 • private test build", style = smallText.copy(fontSize = 10.sp, color = ink.copy(alpha = 0.4f)))
    }
}

@Composable
private fun Pill(label: String, enabled: Boolean = true, action: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = if (enabled) 0.09f else 0.03f))
        .border(1.dp, accent.copy(alpha = if (enabled) 0.35f else 0.1f), RoundedCornerShape(18.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = action).padding(horizontal = 13.dp, vertical = 9.dp)) {
        BasicText(label, style = smallText.copy(color = ink.copy(alpha = if (enabled) 1f else 0.35f)))
    }
}

@Composable
private fun PaperDoll(state: AvatarState, modifier: Modifier) {
    val context = LocalContext.current
    val images = remember {
        fun load(name: String): ImageBitmap = context.assets.open("avatar/$name.webp").use {
            requireNotNull(android.graphics.BitmapFactory.decodeStream(it)).asImageBitmap()
        }
        listOf(load("base"), load("blink_eyes"), load("speaking_mouth"))
    }
    var blinking by remember { mutableStateOf(false) }
    var mouthOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(Random.nextLong(3200, 6700)); blinking = true; delay(135); blinking = false } }
    LaunchedEffect(state) {
        mouthOpen = false
        if (state == AvatarState.Speaking) while (true) { mouthOpen = !mouthOpen; delay(if (mouthOpen) 140 else 100) }
    }
    val breath = rememberInfiniteTransition(label = "maid-breath")
    val scale by breath.animateFloat(0.994f, 1.008f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breath")
    val tilt by animateFloatAsState(if (state == AvatarState.Thinking) -2.4f else if (state == AvatarState.Listening) 1.2f else 0f, label = "head-tilt")
    Canvas(modifier.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = tilt }) {
        fun paint(image: ImageBitmap, x: Float, y: Float, w: Float, h: Float) {
            drawImage(image, dstOffset = IntOffset((size.width * x / 1024).toInt(), (size.height * y / 1536).toInt()),
                dstSize = IntSize((size.width * w / 1024).toInt().coerceAtLeast(1), (size.height * h / 1536).toInt().coerceAtLeast(1)))
        }
        paint(images[0], 0f, 0f, 1024f, 1536f)
        if (blinking) paint(images[1], 275f, 440f, 470f, 130f)
        if (mouthOpen) paint(images[2], 420f, 615f, 185f, 90f)
    }
}
