package com.informedcitizen.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialPlatformTest {

    @Test
    fun `urlFor produces canonical URL for each platform`() {
        assertEquals("https://x.com/RepX",                       SocialPlatform.TWITTER.urlFor("RepX"))
        assertEquals("https://www.facebook.com/RepX",            SocialPlatform.FACEBOOK.urlFor("RepX"))
        assertEquals("https://www.youtube.com/RepX",             SocialPlatform.YOUTUBE.urlFor("RepX"))
        assertEquals("https://www.instagram.com/RepX",           SocialPlatform.INSTAGRAM.urlFor("RepX"))
        assertEquals("https://www.threads.net/@RepX",            SocialPlatform.THREADS.urlFor("RepX"))
        assertEquals("https://bsky.app/profile/RepX.bsky.social", SocialPlatform.BLUESKY.urlFor("RepX.bsky.social"))
    }

    @Test
    fun `urlFor strips a leading at for non-YouTube platforms`() {
        assertEquals("https://x.com/RepX",                  SocialPlatform.TWITTER.urlFor("@RepX"))
        assertEquals("https://www.facebook.com/RepX",       SocialPlatform.FACEBOOK.urlFor("@RepX"))
        assertEquals("https://www.instagram.com/RepX",      SocialPlatform.INSTAGRAM.urlFor("@RepX"))
        assertEquals("https://www.threads.net/@RepX",       SocialPlatform.THREADS.urlFor("@RepX"))
        assertEquals("https://bsky.app/profile/RepX.bsky.social", SocialPlatform.BLUESKY.urlFor("@RepX.bsky.social"))
    }

    @Test
    fun `urlFor preserves a leading at for YouTube`() {
        assertEquals("https://www.youtube.com/@laurafriedman", SocialPlatform.YOUTUBE.urlFor("@laurafriedman"))
        assertEquals("https://www.youtube.com/legacyname",     SocialPlatform.YOUTUBE.urlFor("legacyname"))
    }

    @Test
    fun `fromRaw is case-insensitive and resolves known platforms`() {
        assertEquals(SocialPlatform.TWITTER,  SocialPlatform.fromRaw("TWITTER"))
        assertEquals(SocialPlatform.TWITTER,  SocialPlatform.fromRaw("twitter"))
        assertEquals(SocialPlatform.FACEBOOK, SocialPlatform.fromRaw("Facebook"))
    }

    @Test
    fun `fromRaw returns null for unknown platforms`() {
        assertNull(SocialPlatform.fromRaw("tiktok"))
        assertNull(SocialPlatform.fromRaw(""))
    }
}
