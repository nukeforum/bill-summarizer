package com.informedcitizen.domain.search

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.MemberLegislationItem
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSearchMatcherTest {

    @Test fun `blank query matches everything`() {
        assertTrue(bill().matchesSearchQuery(""))
        assertTrue(bill().matchesSearchQuery("   "))
        assertTrue(legislationItem().matchesSearchQuery(""))
    }

    @Test fun `matches title case-insensitively`() {
        val b = bill(title = "Student Loan Relief Act")
        assertTrue(b.matchesSearchQuery("student loan"))
        assertTrue(b.matchesSearchQuery("RELIEF"))
        assertFalse(b.matchesSearchQuery("wildfire"))
    }

    @Test fun `matches short title and CRS summary`() {
        val b = bill(title = "An Act", shortTitle = "Lulu's Law", summaryCrs = "Expands education grants.")
        assertTrue(b.matchesSearchQuery("lulu's"))
        assertTrue(b.matchesSearchQuery("education"))
    }

    @Test fun `matches committee referral in latest action`() {
        val b = bill(actionText = "Referred to the Committee on Education and the Workforce.")
        assertTrue(b.matchesSearchQuery("education"))
    }

    @Test fun `matches sponsor name`() {
        val b = bill(sponsorName = "Rep. Turner, Michael R.")
        assertTrue(b.matchesSearchQuery("turner"))
    }

    @Test fun `matches bill reference with and without punctuation`() {
        val b = bill(type = "hr", number = "1357")
        assertTrue(b.matchesSearchQuery("hr 1357"))
        assertTrue(b.matchesSearchQuery("hr1357"))
        assertTrue(b.matchesSearchQuery("H.R. 1357"))
        assertFalse(b.matchesSearchQuery("hr 999"))
    }

    @Test fun `all terms must match somewhere`() {
        val b = bill(title = "Education Funding Act", actionText = "Passed the House.")
        assertTrue(b.matchesSearchQuery("education house"))
        assertFalse(b.matchesSearchQuery("education senate"))
    }

    @Test fun `matches policy area subject`() {
        val b = bill(title = "Secure America Act", policyArea = "Armed Forces and National Security")
        assertTrue(b.matchesSearchQuery("armed forces"))
        assertFalse(bill(policyArea = null).matchesSearchQuery("armed forces"))
    }

    @Test fun `legislation item matches title and policy area`() {
        val item = legislationItem(title = "A bill to amend title 20", policyArea = "Education")
        assertTrue(item.matchesSearchQuery("education"))
        assertTrue(item.matchesSearchQuery("amend"))
        assertFalse(legislationItem(policyArea = null).matchesSearchQuery("education"))
    }
}

private fun bill(
    title: String = "Example Bill",
    shortTitle: String? = null,
    summaryCrs: String? = null,
    actionText: String = "Referred to committee.",
    sponsorName: String = "Rep. Doe, Jamie",
    type: String = "hr",
    number: String = "1",
    policyArea: String? = null,
) = Bill(
    id = "$type$number-119",
    congress = 119,
    type = type,
    number = number,
    title = title,
    shortTitle = shortTitle,
    sponsor = Sponsor(name = sponsorName, party = "D", state = "CA"),
    introducedDate = "2026-01-01",
    latestAction = Action(date = "2026-05-01", text = actionText),
    outcome = Outcome.PASSED_HOUSE,
    policyArea = policyArea,
    summaryCrs = summaryCrs,
    congressGovUrl = "https://congress.gov/example",
)

private fun legislationItem(
    title: String = "Some measure",
    policyArea: String? = null,
    actionText: String = "Referred to committee.",
) = MemberLegislationItem(
    id = "hr1-119",
    type = "hr",
    number = "1",
    congress = 119,
    title = title,
    introducedDate = "2026-01-01",
    latestAction = Action(date = "2026-04-01", text = actionText),
    policyArea = policyArea,
)
