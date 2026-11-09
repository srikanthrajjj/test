package com.replykey.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import java.time.Duration

/** What the keyboard knows when the AI key is tapped. Pure Kotlin so it can be tested off-device. */
data class ReplyRequest(
    val appPackage: String,
    val screenText: String,
    val earlierScreenText: String,
    val textBeforeCursor: String,
    val textAfterCursor: String,
    val style: String,
    val userName: String,
)

object Claude {
    // Models that accept the server-side refusal fallback.
    private val FALLBACK_MODELS = setOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-opus-5", "claude-fable-5-1")

    private var client: AnthropicClient? = null
    private var clientKey: String = ""

    @Synchronized
    private fun client(apiKey: String): AnthropicClient {
        val existing = client
        if (existing != null && clientKey == apiKey) return existing
        existing?.close()
        return AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(60))
            .maxRetries(1)
            .build()
            .also { client = it; clientKey = apiKey }
    }

    private const val SYSTEM = """You write replies on behalf of the phone's owner, from inside their keyboard.

You get:
- SCREEN: text currently visible in the app they are typing in (message thread, email, subject, names).
- EARLIER: what was on screen in the same app a moment ago (often the email or message they are replying to).
- FIELD: what is already in the text box, with [CURSOR] marking where your text will be inserted.

Write only the text to insert at [CURSOR]. Rules:
- Reply to the most recent message from the other person. Match the language and register of the conversation.
- Do not repeat a greeting, sign-off or signature that is already in FIELD. If FIELD already has a sign-off after the cursor, write only the body.
- No subject line, no quotes around the reply, no explanations, no placeholders like [Name] unless a fact is truly unknown.
- Never invent facts, dates, passwords, links or commitments. If something must be confirmed, ask for it briefly.
- If the screen text is too thin to know what is being discussed, write a short, safe reply that acknowledges and asks for the detail."""

    fun buildPrompt(r: ReplyRequest): String = buildString {
        append("APP: ").append(r.appPackage).append("\n\n")
        append("SCREEN:\n").append(r.screenText.ifBlank { "(nothing readable — reader service may be off)" }).append("\n\n")
        if (r.earlierScreenText.isNotBlank()) append("EARLIER:\n").append(r.earlierScreenText).append("\n\n")
        append("FIELD:\n").append(r.textBeforeCursor).append("[CURSOR]").append(r.textAfterCursor).append("\n\n")
        if (r.userName.isNotBlank()) append("The owner's name is ").append(r.userName).append(".\n")
        append("Style: ").append(r.style).append(".\n")
        append("Write the text to insert at [CURSOR].")
    }

    /** Blocking call — run off the main thread. */
    fun draft(apiKey: String, model: String, r: ReplyRequest): Result<String> = runCatching {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4000L)
            .system(SYSTEM)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .addUserMessage(buildPrompt(r))
        if (model in FALLBACK_MODELS) {
            builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        val msg = try {
            client(apiKey).messages().create(builder.build())
        } catch (e: UnauthorizedException) {
            throw IllegalStateException("API key rejected. Check it in ReplyKey settings.")
        } catch (e: RateLimitException) {
            throw IllegalStateException("Rate limited. Try again in a moment.")
        } catch (e: AnthropicServiceException) {
            throw IllegalStateException("Claude error ${e.statusCode()}: ${e.message}")
        }
        if (msg.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw IllegalStateException("Claude declined to draft this one.")
        }
        val text = msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("").trim()
        if (text.isEmpty()) throw IllegalStateException("Empty reply. Tap retry.")
        text.removeSurrounding("\"")
    }
}
