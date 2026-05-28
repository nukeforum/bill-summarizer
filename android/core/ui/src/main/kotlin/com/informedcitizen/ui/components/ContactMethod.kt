package com.informedcitizen.ui.components

import com.informedcitizen.pipeline.model.Member

sealed interface ContactMethod {
    data class Phone(val number: String) : ContactMethod
    data class ContactForm(val url: String) : ContactMethod
    data class Website(val url: String) : ContactMethod
    data class Socials(val items: List<SocialItem>) : ContactMethod
}

data class SocialItem(val platform: SocialPlatform, val handle: String) {
    val url: String get() = platform.urlFor(handle)
}

enum class SocialPlatform(
    val label: String,
    private val template: String,
    private val stripLeadingAt: Boolean,
) {
    // YouTube preserves a leading '@' in the handle because modern YouTube
    // URLs include it in the path (youtube.com/@handle). The other
    // platforms strip a stray leading '@' for resilience to YAML quirks;
    // Threads's template re-adds the '@' as part of its URL convention.
    TWITTER("X (Twitter)",  "https://x.com/%s",                 stripLeadingAt = true),
    FACEBOOK("Facebook",    "https://www.facebook.com/%s",      stripLeadingAt = true),
    YOUTUBE("YouTube",      "https://www.youtube.com/%s",       stripLeadingAt = false),
    INSTAGRAM("Instagram",  "https://www.instagram.com/%s",     stripLeadingAt = true),
    THREADS("Threads",      "https://www.threads.net/@%s",      stripLeadingAt = true),
    BLUESKY("Bluesky",      "https://bsky.app/profile/%s",      stripLeadingAt = true);

    fun urlFor(handle: String): String {
        val cleaned = if (stripLeadingAt) handle.trimStart('@') else handle
        return template.format(cleaned)
    }

    companion object {
        fun fromRaw(raw: String): SocialPlatform? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

fun Member.availableContactMethods(): List<ContactMethod> = buildList {
    phone?.takeIf { it.isNotBlank() }?.let { add(ContactMethod.Phone(it)) }
    contactForm?.takeIf { it.isNotBlank() }?.let { add(ContactMethod.ContactForm(it)) }
    website?.takeIf { it.isNotBlank() }?.let { add(ContactMethod.Website(it)) }
    socials
        .mapNotNull { raw -> SocialPlatform.fromRaw(raw.platform)?.let { SocialItem(it, raw.handle) } }
        .takeIf { it.isNotEmpty() }
        ?.let { add(ContactMethod.Socials(it)) }
}
