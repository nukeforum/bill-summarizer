package com.informedcitizen.ui.components

import com.informedcitizen.pipeline.model.Member

sealed interface ContactMethod {
    data class Phone(val number: String) : ContactMethod
    data class ContactForm(val url: String) : ContactMethod
    data class Website(val url: String) : ContactMethod
    // Phase 2 (separate spec): Socials(handles: List<SocialHandle>)
}

fun Member.availableContactMethods(): List<ContactMethod> = buildList {
    phone?.takeIf { it.isNotBlank() }?.let { add(ContactMethod.Phone(it)) }
    contactForm?.takeIf { it.isNotBlank() }?.let { add(ContactMethod.ContactForm(it)) }
    website?.takeIf { it.isNotBlank() }?.let { add(ContactMethod.Website(it)) }
}
