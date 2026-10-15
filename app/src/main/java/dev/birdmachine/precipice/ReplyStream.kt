package dev.birdmachine.precipice

import java.io.BufferedReader
import org.json.JSONObject

internal object ReplyStream {
    fun read(reader: BufferedReader): String {
        val output = StringBuilder(); val eventData = StringBuilder(); var completed = false
        fun consumeEvent() {
            if (eventData.isEmpty()) return
            val data = eventData.toString(); eventData.setLength(0)
            if (data == "[DONE]") return
            val event = JSONObject(data)
            when (event.optString("type")) {
                "response.output_text.delta" -> output.append(event.optString("delta"))
                "response.refusal.delta" -> output.append(event.optString("delta"))
                "response.completed" -> completed = true
                "response.failed", "response.incomplete", "error" -> error("ChatGPT could not complete this reply. Check your connection and plan usage, then retry.")
            }
        }
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) consumeEvent()
            else if (line.startsWith("data:")) {
                if (eventData.isNotEmpty()) eventData.append('\n')
                eventData.append(line.removePrefix("data:").trimStart())
            }
        }
        consumeEvent()
        check(completed) { "Reply stream interrupted. Nothing was added to the conversation; please retry." }
        return output.toString().trim().also { require(it.isNotEmpty()) { "ChatGPT returned no spoken text." } }
    }
}
