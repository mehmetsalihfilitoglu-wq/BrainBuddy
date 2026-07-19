package com.edumio.app.analytics

/**
 * Pure guard that keeps sensitive content OUT of analytics. Event params are filtered to drop any key that
 * looks like PII or question/solution content, and string values are truncated. Unit-tested so the
 * "never log stems / options / solutions / emails / tokens" rule is enforced independent of the SDK.
 */
object AnalyticsSafety {

    private val BLOCKED_SUBSTRINGS = listOf(
        "email", "password", "token", "secret",
        "stem", "question_text", "questiontext", "option", "choice", "answer_text",
        "solution", "explanation", "display_name", "displayname", "full_name", "username",
    )

    private const val MAX_STRING = 100
    private const val MAX_EVENT_NAME = 40

    /** True if a key is safe to send (not PII/content). */
    fun isKeyAllowed(key: String): Boolean {
        val k = key.lowercase()
        return BLOCKED_SUBSTRINGS.none { k.contains(it) }
    }

    /** Drop disallowed keys and truncate string values. */
    fun sanitize(params: Map<String, Any?>): Map<String, Any?> =
        params.asSequence()
            .filter { isKeyAllowed(it.key) }
            .associate { (k, v) -> k to (if (v is String) v.take(MAX_STRING) else v) }

    /** Firebase event names must be short + non-blank; truncate to the platform limit. */
    fun safeEventName(name: String): String = name.trim().take(MAX_EVENT_NAME)
}
