package com.informedcitizen.ui.components

import com.informedcitizen.pipeline.model.Member
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberContactMethodsTest {

    private fun member(
        phone: String? = null,
        contactForm: String? = null,
        website: String? = null,
    ): Member = Member(
        bioguideId = "T000001",
        name = "Test Rep",
        party = "D",
        state = "CA",
        district = 1,
        chamber = "house",
        photoUrl = null,
        officialUrl = null,
        sponsoredCount = 0,
        cosponsoredCount = 0,
        address = null,
        phone = phone,
        contactForm = contactForm,
        website = website,
    )

    @Test
    fun `empty fields produce empty list`() {
        assertEquals(emptyList<ContactMethod>(), member().availableContactMethods())
    }

    @Test
    fun `whitespace-only fields are dropped`() {
        val m = member(phone = "  ", contactForm = "", website = "\t")
        assertEquals(emptyList<ContactMethod>(), m.availableContactMethods())
    }

    @Test
    fun `phone only`() {
        val m = member(phone = "(202) 224-3441")
        assertEquals(
            listOf(ContactMethod.Phone("(202) 224-3441")),
            m.availableContactMethods(),
        )
    }

    @Test
    fun `all three present in fixed order Phone-ContactForm-Website`() {
        val m = member(
            phone = "(202) 224-3441",
            contactForm = "https://example.gov/contact",
            website = "https://example.gov",
        )
        assertEquals(
            listOf(
                ContactMethod.Phone("(202) 224-3441"),
                ContactMethod.ContactForm("https://example.gov/contact"),
                ContactMethod.Website("https://example.gov"),
            ),
            m.availableContactMethods(),
        )
    }

    @Test
    fun `form and website without phone preserves order`() {
        val m = member(
            contactForm = "https://example.gov/contact",
            website = "https://example.gov",
        )
        assertEquals(
            listOf(
                ContactMethod.ContactForm("https://example.gov/contact"),
                ContactMethod.Website("https://example.gov"),
            ),
            m.availableContactMethods(),
        )
    }
}
