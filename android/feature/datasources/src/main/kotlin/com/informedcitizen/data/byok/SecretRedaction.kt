package com.informedcitizen.data.byok

import java.util.Collections
import java.util.IdentityHashMap

/** Written over any credential found in text that leaves this process or reaches the screen. */
internal const val REDACTED: String = "***"

/**
 * Backstop for a credential that is not the one we hold — a stale key
 * still in flight, or a second credential added later. Matches the
 * `api_key=` / `apikey=` / `key=` query parameter and its value inside a
 * URL embedded in free text; Ktor's timeout messages read
 * `Request timeout has expired [url=…, request_timeout=… ms]`, so the
 * value runs to the next `&`, `,`, `]`, or whitespace.
 */
private val API_KEY_QUERY_PARAM =
    Regex("([?&](?:api[_-]?key|key)=)[^&\\s\\],]*", RegexOption.IGNORE_CASE)

/**
 * How deep a rebuilt chain goes. Detection is unbounded — see
 * [mentionsSecret] — so a secret below this depth still triggers a
 * rebuild; the link carrying it is then dropped rather than copied,
 * which errs toward scrubbing more, never toward uploading unscanned
 * text. Cause chains are shallow in practice.
 */
private const val MAX_CAUSE_DEPTH = 8

/**
 * The user's Congress.gov API key must never reach Crashlytics or the
 * screen. [CongressClient][com.informedcitizen.pipeline.http.CongressClient]
 * and [ByokKeyValidator] keep it out of the URL so nothing can capture it
 * in the first place; this is the second line, for the day someone adds a
 * query parameter back or introduces a new path that echoes a credential.
 *
 * Returns [text] with every occurrence of [secret] — and any `api_key`
 * query value, whoever's it is — replaced by [REDACTED]. A null or blank
 * [secret] still gets the query-parameter scrub.
 */
internal fun redactSecret(text: String?, secret: String?): String? {
    if (text == null) return null
    val withoutSecret = if (secret.isNullOrBlank()) text else text.replace(secret, REDACTED)
    return API_KEY_QUERY_PARAM.replace(withoutSecret) { match ->
        match.groupValues[1] + REDACTED
    }
}

/**
 * A stand-in carrying the original throwable's type name, scrubbed
 * message, and stack trace. The original is deliberately *not* attached
 * as the cause — that would upload the very message we just scrubbed.
 */
internal class RedactedThrowable(
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause)

/**
 * [throwable] with [secret] scrubbed out of its message chain, ready for
 * `CrashReporter.recordNonFatal`. `Throwable.message` is immutable, so a
 * throwable that does mention the secret is replaced wholesale by a
 * [RedactedThrowable] carrying the same type name and stack trace; the
 * cause chain is rebuilt the same way. When nothing needs scrubbing the
 * original instance is returned untouched, so ordinary failures keep
 * their real type and Crashlytics grouping.
 */
internal fun redactThrowable(throwable: Throwable, secret: String?): Throwable =
    if (mentionsSecret(throwable, secret)) rebuild(throwable, secret, depth = 0) else throwable

/**
 * Whether [secret] appears anywhere in [throwable]'s message chain.
 * Walks the whole chain, because `FirebaseCrashlytics.recordException`
 * uploads the whole chain; a bound here shallower than that would let a
 * deeply nested secret through untouched. Identity-tracked rather than
 * depth-capped so a cyclic chain still terminates.
 */
private fun mentionsSecret(throwable: Throwable, secret: String?): Boolean {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = throwable
    while (current != null && seen.add(current)) {
        if (redactSecret(current.message, secret) != current.message) return true
        current = current.cause
    }
    return false
}

private fun rebuild(throwable: Throwable, secret: String?, depth: Int): Throwable {
    val cause = throwable.cause
        ?.takeIf { it !== throwable && depth < MAX_CAUSE_DEPTH }
        ?.let { rebuild(it, secret, depth + 1) }
    val redacted = RedactedThrowable(
        message = "${throwable::class.java.name}: ${redactSecret(throwable.message, secret)}",
        cause = cause,
    )
    redacted.stackTrace = throwable.stackTrace
    return redacted
}

/**
 * One failure's detail text for the screen: [throwable]'s message with
 * [secret] scrubbed out, falling back to the exception's type name and
 * then to a fixed string when the message is null or scrubs away to
 * nothing.
 *
 * Shared by the two on-screen egress paths — the "Fetch now" result line
 * ([fetchFailureText][com.informedcitizen.ui.datasources.fetchFailureText])
 * and the key-entry supporting text ([ByokKeyValidator]) — so they cannot
 * drift apart on what a scrubbed-to-empty message degrades to.
 */
internal fun redactedDetail(throwable: Throwable, secret: String?): String =
    redactSecret(throwable.message, secret)?.takeIf { it.isNotBlank() }
        ?: throwable::class.simpleName
        ?: "unknown error"
