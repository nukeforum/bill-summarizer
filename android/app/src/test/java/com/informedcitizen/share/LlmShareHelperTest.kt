package com.informedcitizen.share

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import org.junit.Assert.assertEquals
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

    @Test
    fun `crs summary strips paragraph tags into blank-line-separated text`() {
        val out = LlmShareHelper.crsSummaryToPlainText(
            "<p>First paragraph.</p><p>Second paragraph.</p>",
        )

        assertEquals("First paragraph.\n\nSecond paragraph.", out)
    }

    @Test
    fun `crs summary decodes common entities`() {
        val out = LlmShareHelper.crsSummaryToPlainText(
            "<p>Centers for Medicare &amp; Medicaid Services&nbsp;(CMS).</p>",
        )

        assertEquals("Centers for Medicare & Medicaid Services (CMS).", out)
    }

    @Test
    fun `crs summary decodes numeric entities`() {
        val out = LlmShareHelper.crsSummaryToPlainText("<p>rule&#8212;the notice&#39;s scope</p>")

        assertEquals("rule—the notice's scope", out)
    }

    @Test
    fun `crs summary renders list items as bullets`() {
        val out = LlmShareHelper.crsSummaryToPlainText(
            "<p>It does:</p><ul><li>one thing</li><li>another thing</li></ul>",
        )

        assertEquals("It does:\n\n• one thing\n• another thing", out)
    }

    @Test
    fun `crs summary removes inline formatting tags but keeps their text`() {
        val out = LlmShareHelper.crsSummaryToPlainText(
            "<p>The <strong>Act</strong> <em>also</em> <b>repeals</b> section 5.</p>",
        )

        assertEquals("The Act also repeals section 5.", out)
    }

    @Test
    fun `crs summary output contains no residual markup when used as prompt body`() {
        val body = LlmShareHelper.crsSummaryToPlainText(
            "<p>Prohibits the model &amp; nullifies a notice.</p>",
        )
        val out = LlmShareHelper.buildPrompt(fixture(), body = body)

        assertFalse("no paragraph tag", out.contains("<p>"))
        assertFalse("no raw ampersand entity", out.contains("&amp;"))
        assertTrue("decoded body present", out.contains("Prohibits the model & nullifies a notice."))
    }

    @Test
    fun `crs summary leaves already-plain text unchanged apart from trimming`() {
        val out = LlmShareHelper.crsSummaryToPlainText("  Just a plain sentence.  ")

        assertEquals("Just a plain sentence.", out)
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
