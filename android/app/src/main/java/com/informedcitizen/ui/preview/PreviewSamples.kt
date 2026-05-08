package com.informedcitizen.ui.preview

import androidx.compose.runtime.Composable
import com.informedcitizen.data.model.Action
import com.informedcitizen.data.model.Bill
import com.informedcitizen.data.model.ChamberCalendar
import com.informedcitizen.data.model.Member
import com.informedcitizen.data.model.MemberLegislationItem
import com.informedcitizen.data.model.Outcome
import com.informedcitizen.data.model.SessionCalendar
import com.informedcitizen.data.model.SessionCalendarSource
import com.informedcitizen.data.model.Sponsor
import com.informedcitizen.theme.InformedCitizenTheme
import com.informedcitizen.theme.ThemePreference
import java.time.LocalDate

/**
 * Theme wrapper for `@Preview` composables. Locks the resolved theme to
 * Material (system mode) so previews render with the platform default
 * palette regardless of the in-app default (which is Solarized). Use
 * this in every preview's wrapper instead of `InformedCitizenTheme`
 * directly.
 */
@Composable
fun MaterialPreviewTheme(content: @Composable () -> Unit) {
    InformedCitizenTheme(preference = ThemePreference.MATERIAL_SYSTEM, content = content)
}

/**
 * Shared sample data for `@Preview` composables. Lives in `main` so previews
 * across packages can reuse it. Not for production use — these are display
 * fixtures.
 */

fun sampleSponsor(
    name: String = "Maria Cantwell",
    party: String = "D",
    state: String = "WA",
): Sponsor = Sponsor(name = name, party = party, state = state)

fun sampleAction(
    date: String = "2026-04-15",
    text: String = "Passed Senate by Yea-Nay Vote. 68 - 31. Record Vote Number: 142.",
): Action = Action(date = date, text = text)

fun sampleBill(
    id: String = "119-s-1234",
    congress: Int = 119,
    type: String = "s",
    number: String = "1234",
    title: String = "A bill to modernize the Federal data infrastructure for public-benefit programs.",
    shortTitle: String? = "Federal Data Modernization Act",
    sponsor: Sponsor = sampleSponsor(),
    introducedDate: String = "2026-03-02",
    latestAction: Action = sampleAction(),
    outcome: Outcome = Outcome.PASSED_SENATE,
    summaryCrs: String? = "<p>This bill establishes a unified data architecture across federal " +
        "agencies that administer public benefits, with new standards for interoperability, " +
        "privacy controls, and audit logging.</p>",
    textUrlHtml: String? = "https://www.congress.gov/119/bills/s1234/BILLS-119s1234rs.htm",
    textUrlXml: String? = null,
    textUrlPdf: String? = null,
    congressGovUrl: String = "https://www.congress.gov/bill/119th-congress/senate-bill/1234",
): Bill = Bill(
    id = id,
    congress = congress,
    type = type,
    number = number,
    title = title,
    shortTitle = shortTitle,
    sponsor = sponsor,
    introducedDate = introducedDate,
    latestAction = latestAction,
    outcome = outcome,
    summaryCrs = summaryCrs,
    textUrlHtml = textUrlHtml,
    textUrlXml = textUrlXml,
    textUrlPdf = textUrlPdf,
    congressGovUrl = congressGovUrl,
)

val sampleBills: List<Bill> = listOf(
    sampleBill(),
    sampleBill(
        id = "119-hr-2200",
        type = "hr",
        number = "2200",
        title = "Veterans Healthcare Access Improvement Act",
        shortTitle = "Veterans Healthcare Access Improvement Act",
        sponsor = sampleSponsor(name = "Mike Lawler", party = "R", state = "NY"),
        outcome = Outcome.ENACTED,
        latestAction = sampleAction(
            date = "2026-04-22",
            text = "Became Public Law No: 119-21.",
        ),
    ),
    sampleBill(
        id = "119-hr-3018",
        type = "hr",
        number = "3018",
        title = "Reform of small-business administration loan program for rural cooperatives.",
        shortTitle = null,
        sponsor = sampleSponsor(name = "Yadira Caraveo", party = "D", state = "CO"),
        outcome = Outcome.FAILED,
        summaryCrs = null,
        latestAction = sampleAction(
            date = "2026-02-05",
            text = "Motion to suspend the rules and pass the bill failed by recorded vote: 188 - 240.",
        ),
    ),
)

fun sampleMember(
    bioguideId: String = "C000127",
    name: String = "Maria Cantwell",
    party: String = "D",
    state: String = "WA",
    district: Int? = null,
    chamber: String = "senate",
    photoUrl: String? = null,
    officialUrl: String? = null,
    sponsoredCount: Int = 24,
    cosponsoredCount: Int = 132,
    address: String? = null,
    phone: String? = null,
): Member = Member(
    bioguideId = bioguideId,
    name = name,
    party = party,
    state = state,
    district = district,
    chamber = chamber,
    photoUrl = photoUrl,
    officialUrl = officialUrl,
    sponsoredCount = sponsoredCount,
    cosponsoredCount = cosponsoredCount,
    address = address,
    phone = phone,
)

val sampleSenatorD: Member = sampleMember()

val sampleSenatorR: Member = sampleMember(
    bioguideId = "C001098",
    name = "Bill Cassidy",
    party = "R",
    state = "LA",
)

val sampleRepresentative: Member = sampleMember(
    bioguideId = "C001125",
    name = "Yadira Caraveo",
    party = "D",
    state = "CO",
    district = 8,
    chamber = "house",
)

fun sampleLegislationItem(
    id: String = "119-s-1234",
    type: String = "s",
    number: String = "1234",
    congress: Int = 119,
    title: String = "Federal Data Modernization Act",
    introducedDate: String = "2026-03-02",
    latestAction: Action = sampleAction(),
    policyArea: String? = "Government Operations and Politics",
): MemberLegislationItem = MemberLegislationItem(
    id = id,
    type = type,
    number = number,
    congress = congress,
    title = title,
    introducedDate = introducedDate,
    latestAction = latestAction,
    policyArea = policyArea,
)

val sampleLegislation: List<MemberLegislationItem> = listOf(
    sampleLegislationItem(),
    sampleLegislationItem(
        id = "119-s-2107",
        number = "2107",
        title = "A bill to authorize the Federal Trade Commission to study the effects of " +
            "consolidation in agricultural input markets.",
        latestAction = sampleAction(
            date = "2026-04-01",
            text = "Read twice and referred to the Committee on Agriculture, Nutrition, and Forestry.",
        ),
    ),
    sampleLegislationItem(
        id = "119-sjres-12",
        type = "sjres",
        number = "12",
        title = "A joint resolution disapproving the rule submitted by the Department of Energy.",
        latestAction = sampleAction(
            date = "2026-03-20",
            text = "Placed on Senate Legislative Calendar under General Orders.",
        ),
    ),
)

/**
 * A minimal session calendar with House and Senate session days bracketing a
 * fixed reference date so the calendar grid renders both in-session and recess
 * cells in previews. Reference date: 2026-05-08.
 */
fun sampleSessionCalendar(): SessionCalendar = SessionCalendar(
    generatedAt = "2026-05-01T00:00:00Z",
    source = SessionCalendarSource(
        house = "https://www.majorityleader.gov/calendar",
        senate = "https://www.senate.gov/legislative/2026_schedule.htm",
    ),
    chambers = mapOf(
        "house" to ChamberCalendar(
            sessionDays = listOf(
                "2026-05-05",
                "2026-05-06",
                "2026-05-07",
                "2026-05-08",
                "2026-05-12",
                "2026-05-13",
                "2026-05-14",
                "2026-05-19",
                "2026-05-20",
                "2026-06-02",
                "2026-06-03",
                "2026-06-04",
            ),
        ),
        "senate" to ChamberCalendar(
            sessionDays = listOf(
                "2026-05-05",
                "2026-05-06",
                "2026-05-07",
                "2026-05-12",
                "2026-05-13",
                "2026-05-14",
                "2026-05-19",
                "2026-05-20",
                "2026-05-21",
                "2026-06-01",
                "2026-06-02",
                "2026-06-03",
            ),
        ),
    ),
)

@Suppress("unused")
private val SAMPLE_REFERENCE_DATE: LocalDate = LocalDate.of(2026, 5, 8)
