package dev.birdmachine.precipice

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

enum class AvatarState { Idle, Listening, Thinking, Speaking, Error }

class ShellModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("precipice_shell", 0)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private val generation = AtomicInteger()
    private val signInGeneration = AtomicInteger()
    private val credentials = CredentialStore(app)
    private val brain = ChatGptConnection(credentials)
    private var recognizer: SpeechRecognizer? = null
    private var recognitionGeneration = -1
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var activeUtterance = ""
    private var turns = listOf<ChatTurn>()

    var state by mutableStateOf(AvatarState.Idle); private set
    var status by mutableStateOf("Tap to talk, or preview an expression."); private set
    var transcript by mutableStateOf(""); private set
    var response by mutableStateOf(""); private set
    var account by mutableStateOf(runCatching { brain.accountLabel() }.getOrDefault("")); private set
    var connected by mutableStateOf(runCatching { brain.connected() }.getOrDefault(false)); private set
    var connecting by mutableStateOf(false); private set
    var models by mutableStateOf(listOf<AvailableModel>()); private set
    var selectedModel by mutableStateOf(prefs.getString("model", "") ?: ""); private set
    var showMaid by mutableStateOf(prefs.getBoolean("maid", true)); private set
    var ttsRate by mutableStateOf(prefs.getFloat("tts_rate", 1.2f)); private set
    var speechRoute by mutableStateOf("Speech recognition not started"); private set

    init {
        tts = TextToSpeech(app) { result -> main.post {
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                // Prefer an installed offline voice. Network voices are never selected here.
                val voice = tts?.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale.language == "en" }
                if (voice != null) tts?.voice = voice else { ttsReady = false; status = "Install an offline English phone voice to enable speech output." }
                tts?.setSpeechRate(ttsRate)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { main.post { if (id == activeUtterance) state = AvatarState.Speaking } }
                    override fun onDone(id: String?) { main.post { if (id == activeUtterance) { state = AvatarState.Idle; status = "Ready when you are." } } }
                    @Deprecated("Android compatibility") override fun onError(id: String?) { main.post { if (id == activeUtterance) fail("Speech output unavailable. The reply is shown below.") } }
                })
            }
        } }
        if (connected) refreshModels()
    }

    fun toggleAvatar() { showMaid = !showMaid; prefs.edit().putBoolean("maid", showMaid).apply() }
    fun setRate(value: Float) { ttsRate = value; tts?.setSpeechRate(value); prefs.edit().putFloat("tts_rate", value).apply() }
    fun selectModel(slug: String) {
        stop(); selectedModel = slug; prefs.edit().putString("model", slug).apply()
    }
    fun preview(expression: AvatarState) {
        stop(); state = expression; status = "Expression preview: ${expression.name.lowercase()}"
        if (expression == AvatarState.Speaking) speak("Hello Birdie. This is the Precipice voice and expression preview.")
    }
    fun clearConversation() { stop(); turns = emptyList(); transcript = ""; response = ""; status = "New conversation. Saved connection kept." }

    fun startListening() {
        stop()
        val app = getApplication<Application>()
        val local = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
        if (!local && !SpeechRecognizer.isRecognitionAvailable(app)) { fail("No speech recognizer is installed. You can type a message instead."); return }
        recognitionGeneration = generation.get()
        val attempt = recognitionGeneration
        recognizer?.destroy()
        recognizer = if (local) SpeechRecognizer.createOnDeviceSpeechRecognizer(app) else SpeechRecognizer.createSpeechRecognizer(app)
        speechRoute = if (local) "Speech: on device" else "Speech: system service • may use network"
        state = AvatarState.Listening; status = "Listening… tap again to finish."; transcript = ""
        recognizer?.setRecognitionListener(object : RecognitionListener {
            private fun valid() = generation.get() == attempt
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (valid()) { state = AvatarState.Thinking; status = "Finishing transcription…" } }
            override fun onError(error: Int) { if (valid()) fail(when(error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap to try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed to talk. You can type instead."
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "English speech recognition isn't installed. Download the phone's offline language pack or type a message."
                else -> "Speech recognition stopped ($error). Tap to retry or type a message."
            }) }
            override fun onResults(results: Bundle?) {
                if (!valid()) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) fail("No speech was recognized. Please try again.") else send(text)
            }
            override fun onPartialResults(results: Bundle?) { if (valid()) transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty() }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        runCatching { recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }) }.onFailure { fail("Unable to start the microphone. Check permission and retry.") }
    }
    fun finishListening() { recognizer?.stopListening(); state = AvatarState.Thinking; status = "Finishing transcription…" }
    fun send(text: String) {
        val clean = text.trim(); if (clean.isEmpty() || connecting) return
        stop(); transcript = clean
        if (!connected) { status = "Speech captured. Open settings and Continue with ChatGPT to get a reply."; return }
        if (selectedModel.isBlank()) { fail("Choose an available model in settings first."); refreshModels(); return }
        val attempt = generation.get(); val model = selectedModel
        val requestTurns = turns.takeLast(20) + ChatTurn("user", clean)
        state = AvatarState.Thinking; status = "ChatGPT is thinking…"
        worker.execute {
            runCatching { brain.respond(model, requestTurns) }.fold(
                onSuccess = { reply -> main.post { if (generation.get() == attempt) {
                    turns = requestTurns + ChatTurn("assistant", reply); response = reply; speak(reply)
                } } },
                onFailure = { error -> main.post { if (generation.get() == attempt) fail(error.message ?: "Could not complete the reply.") } })
        }
    }
    private fun speak(text: String) {
        if (!ttsReady) { state = AvatarState.Idle; status = "Reply ready. Phone TTS isn't ready yet."; return }
        activeUtterance = "precipice-${generation.get()}-${System.nanoTime()}"
        tts?.setSpeechRate(ttsRate)
        state = AvatarState.Speaking; status = "Speaking • tap to interrupt"
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), activeUtterance) == TextToSpeech.ERROR) fail("Phone TTS couldn't speak. The reply is shown below.")
    }
    fun stop() {
        generation.incrementAndGet(); activeUtterance = ""
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        tts?.stop(); state = AvatarState.Idle; status = "Ready when you are."
    }
    fun pauseAudio() { if (state != AvatarState.Idle && !connecting) stop() }
    fun fail(message: String) { state = AvatarState.Error; status = message }
    fun signIn(openBrowser: (String) -> Unit) {
        if (connecting) return
        stop(); val attempt = signInGeneration.incrementAndGet(); connecting = true; status = "Finish ChatGPT sign-in in your browser, then return here."
        worker.execute {
            if (signInGeneration.get() != attempt) return@execute
            runCatching { brain.signIn { url -> main.post { if (signInGeneration.get() == attempt) openBrowser(url) } } }.fold(
                onSuccess = { main.post { if (signInGeneration.get() != attempt) return@post; connecting = false; connected = brain.connected(); account = brain.accountLabel(); status = "ChatGPT connected. Loading available models…"; refreshModels() } },
                onFailure = { error -> main.post { if (signInGeneration.get() != attempt) return@post; connecting = false; fail(error.message ?: "Sign-in could not complete. Please retry.") } })
        }
    }
    fun cancelSignIn() { signInGeneration.incrementAndGet(); brain.cancelSignIn(); connecting = false; status = "Sign-in cancelled." }
    fun refreshModels() {
        val attempt = generation.get()
        worker.execute {
            runCatching { brain.models() }.fold(
                onSuccess = { choices -> main.post { if (generation.get() == attempt) {
                    models = choices
                    if (choices.none { it.slug == selectedModel }) {
                        selectedModel = choices.firstOrNull()?.slug.orEmpty(); prefs.edit().putString("model", selectedModel).apply()
                    }
                    status = if (choices.isEmpty()) "No models are available for this ChatGPT connection." else "ChatGPT connected • ready to talk"
                } } },
                onFailure = { error -> main.post { if (generation.get() == attempt) fail(error.message ?: "Unable to load models.") } })
        }
    }
    fun disconnect() {
        stop(); cancelSignIn(); val attempt = signInGeneration.get(); connected = false; models = emptyList(); turns = emptyList()
        worker.execute {
            val revoked = runCatching { brain.disconnect() }.getOrDefault(false)
            main.post { if (signInGeneration.get() != attempt) return@post; connected = false; connecting = false; models = emptyList(); turns = emptyList()
                status = if (revoked) "ChatGPT disconnected." else "Disconnected locally. Remote revocation wasn't confirmed; disconnect Precipice in ChatGPT settings too." }
        }
    }
    override fun onCleared() {
        stop(); brain.cancelSignIn(); tts?.shutdown(); worker.shutdownNow(); super.onCleared()
    }
}
