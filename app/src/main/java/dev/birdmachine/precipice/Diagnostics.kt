package dev.birdmachine.precipice

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bounded diagnostic events. Never record callback URLs, OAuth codes, state, tokens or messages. */
object Diagnostics {
    private val entries = ArrayDeque<String>()
    @Synchronized fun event(stage: String, detail: String = "") {
        val safe = (stage + if (detail.isBlank()) "" else ": " + detail)
            .replace(Regex("[\\r\\n]"), " ").take(180)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        entries.addLast("$timestamp $safe")
        while (entries.size > 250) entries.removeFirst()
        Log.i("PrecipiceAuth", safe)
    }
    @Synchronized fun export(): String =
        "Precipice diagnostics (no credentials or message contents)\n" +
            "Android SDK: ${android.os.Build.VERSION.SDK_INT}\n\n" +
            entries.joinToString("\n")
}
