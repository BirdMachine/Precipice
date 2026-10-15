package dev.birdmachine.precipice

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyStreamTest {
    private fun read(text: String) = ReplyStream.read(text.reader().buffered())
    @Test fun collectsTextOnlyAfterCompleted() {
        assertEquals("Hello Birdie", read("""
            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"Hello "}

            data: {"type":"response.output_text.delta","delta":"Birdie"}

            data: {"type":"response.completed"}

            data: [DONE]
        """.trimIndent()))
    }
    @Test(expected = IllegalStateException::class) fun rejectsTruncatedReply() {
        read("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\ndata: [DONE]\n")
    }
    @Test(expected = IllegalStateException::class) fun rejectsFailedReplyAfterText() {
        read("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\ndata: {\"type\":\"response.failed\"}\n\n")
    }
    @Test fun supportsMultilineAndFinalEventWithoutBlankLine() {
        assertEquals("No thank you", read("data: {\"type\":\"response.refusal.delta\",\n" +
            "data: \"delta\":\"No thank you\"}\n\ndata: {\"type\":\"response.completed\"}"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyCompletedReply() {
        read("data: {\"type\":\"response.completed\"}\n\n")
    }
}
