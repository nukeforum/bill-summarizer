package com.informedcitizen.share

import com.informedcitizen.data.model.Action
import com.informedcitizen.data.model.Bill
import com.informedcitizen.data.model.Outcome
import com.informedcitizen.data.model.Sponsor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmShareHelperTest {

    @Test
    fun `prompt includes all four section headings in order`() {
        val out = LlmShareHelper.buildPrompt(fixture(), body = "Bill text here.")

        val whatIdx = out.indexOf("## What the bill would do")
        val whoIdx = out.indexOf("## Who is affected")
        val provIdx = out.indexOf("## Key provisions")
        val notableIdx = out.indexOf("## Notable or contested elements")

        assertTrue("'What the bill would do' present", whatIdx >= 0)
        assertTrue("'Who is affected' after 'What'", whoIdx > whatIdx)
        assertTrue("'Key provisions' after 'Who'", provIdx > whoIdx)
        assertTrue("'Notable or contested' after 'Key'", notableIdx > provIdx)
    }

    @Test
    fun `prompt includes anti-hallucination guard`() {
        val out = LlmShareHelper.buildPrompt(fixture(), body = "Bill text here.")

        assertTrue(
            "anti-hallucination sentinel present",
            out.contains("\"not specified in this excerpt\""),
        )
    }

    @Test
    fun `metadata block interpolates bill fields`() {
        val out = LlmShareHelper.buildPrompt(fixture(), body = "Bill text here.")

        assertTrue("formatted bill ref", out.contains("H.R. 1234"))
        assertTrue("title", out.contains("An Act to do something specific"))
        assertTrue("outcome display", out.contains("Status: Enacted on 2026-04-20"))
        assertTrue("latest action text", out.contains("Latest action: Became Public Law No: 119-12"))
        assertTrue("introduced date", out.contains("Introduced: 2026-01-15"))
        assertTrue("sponsor block", out.contains("Sponsor: Jane Doe (D-CA)"))
    }

    @Test
    fun `shortTitle is omitted when null`() {
        val out = LlmShareHelper.buildPrompt(fixture(shortTitle = null), body = "x")

        assertFalse("Also-known-as line absent", out.contains("Also known as:"))
    }

    @Test
    fun `shortTitle is omitted when blank`() {
        val out = LlmShareHelper.buildPrompt(fixture(shortTitle = "   "), body = "x")

        assertFalse("Also-known-as line absent for blank", out.contains("Also known as:"))
    }

    @Test
    fun `shortTitle is included when non-blank`() {
        val out = LlmShareHelper.buildPrompt(
            fixture(shortTitle = "Friendly Name Act"),
            body = "x",
        )

        assertTrue(out.contains("Also known as: Friendly Name Act"))
    }

    @Test
    fun `null body falls back to congress dot gov pointer`() {
        val out = LlmShareHelper.buildPrompt(fixture(), body = null)

        assertTrue(
            out.contains(
                "(No bill text included; see Congress.gov: " +
                    "https://www.congress.gov/bill/119th-congress/house-bill/1234)",
            ),
        )
    }

    @Test
    fun `blank body falls back to congress dot gov pointer`() {
        val out = LlmShareHelper.buildPrompt(fixture(), body = " \n  \t ")

        assertTrue(
            out.contains(
                "(No bill text included; see Congress.gov: " +
                    "https://www.congress.gov/bill/119th-congress/house-bill/1234)",
            ),
        )
    }

    @Test
    fun `body over 50k chars is truncated with sentinel`() {
        val body = "x".repeat(60_000)
        val out = LlmShareHelper.buildPrompt(fixture(), body = body)

        assertTrue("truncation sentinel present", out.contains("[Bill text truncated. Full text: "))
        assertTrue("first 50k chars present", out.contains("x".repeat(50_000)))
        assertFalse("over-cap chars excluded", out.contains("x".repeat(50_001)))
    }

    @Test
    fun `body at or under 50k chars is not truncated`() {
        val body = "x".repeat(50_000)
        val out = LlmShareHelper.buildPrompt(fixture(), body = body)

        assertFalse("no truncation sentinel", out.contains("[Bill text truncated."))
        assertTrue("full body present", out.contains("x".repeat(50_000)))
    }

    private fun fixture(shortTitle: String? = null): Bill = Bill(
        id = "hr-119-1234",
        congress = 119,
        type = "hr",
        number = "1234",
        title = "An Act to do something specific",
        shortTitle = shortTitle,
        sponsor = Sponsor(name = "Jane Doe", party = "D", state = "CA"),
        introducedDate = "2026-01-15",
        latestAction = Action(date = "2026-04-20", text = "Became Public Law No: 119-12"),
        outcome = Outcome.ENACTED,
        summaryCrs = null,
        textUrlHtml = null,
        textUrlXml = null,
        textUrlPdf = null,
        congressGovUrl = "https://www.congress.gov/bill/119th-congress/house-bill/1234",
    )
}
