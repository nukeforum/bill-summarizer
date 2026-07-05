package com.informedcitizen.ui.components

import com.informedcitizen.pipeline.model.Member
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberContactMethodsTest {

    private fun member(
        phone: String? = null,
        contactForm: String? = null,
        website: String? = null,
        socials: List<com.informedcitizen.pipeline.model.SocialHandle> = emptyList(),
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
        socials = socials,
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
    fun `contact form suppresses website`() {
        val m = member(
            phone = "(202) 224-3441",
            contactForm = "https://example.gov/contact",
            website = "https://example.gov",
        )
        assertEquals(
            listOf(
                ContactMethod.Phone("(202) 224-3441"),
                ContactMethod.ContactForm("https://example.gov/contact"),
            ),
            m.availableContactMethods(),
        )
    }

    @Test
    fun `website appears as fallback when contact form is absent`() {
        val m = member(
            phone = "(202) 224-3441",
            website = "https://example.gov",
        )
        assertEquals(
            listOf(
                ContactMethod.Phone("(202) 224-3441"),
                ContactMethod.Website("https://example.gov"),
            ),
            m.availableContactMethods(),
        )
    }

    @Test
    fun `blank contact form does not suppress website`() {
        val m = member(
            contactForm = "  ",
            website = "https://example.gov",
        )
        assertEquals(
            listOf(ContactMethod.Website("https://example.gov")),
            m.availableContactMethods(),
        )
    }

    @Test
    fun `socials appended in fixed order when present`() {
        val m = member(
            phone = "(202) 224-3441",
            socials = listOf(
                com.informedcitizen.pipeline.model.SocialHandle("twitter", "RepX"),
                com.informedcitizen.pipeline.model.SocialHandle("facebook", "RepX"),
            ),
        )
        val methods = m.availableContactMethods()
        assertEquals(2, methods.size)
        assertEquals(ContactMethod.Phone("(202) 224-3441"), methods[0])
        val socialsMethod = methods[1] as ContactMethod.Socials
        assertEquals(
            listOf(
                SocialItem(SocialPlatform.TWITTER, "RepX"),
                SocialItem(SocialPlatform.FACEBOOK, "RepX"),
            ),
            socialsMethod.items,
        )
    }

    @Test
    fun `unknown platform handles are silently dropped`() {
        val m = member(
            socials = listOf(
                com.informedcitizen.pipeline.model.SocialHandle("twitter", "RepX"),
                com.informedcitizen.pipeline.model.SocialHandle("tiktok", "RepY"),
            ),
        )
        val methods = m.availableContactMethods()
        assertEquals(1, methods.size)
        val socialsMethod = methods[0] as ContactMethod.Socials
        assertEquals(listOf(SocialItem(SocialPlatform.TWITTER, "RepX")), socialsMethod.items)
    }

    @Test
    fun `empty socials list does not add a Socials method`() {
        val m = member(phone = "(202) 224-3441", socials = emptyList())
        val methods = m.availableContactMethods()
        assertEquals(listOf(ContactMethod.Phone("(202) 224-3441")), methods)
    }
}
