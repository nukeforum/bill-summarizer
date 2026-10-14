import Foundation
import ICClients
import ICFeatures
import ICModels
import Testing

@Suite("Calendar presentation")
struct CalendarPresentationTests {
  private let today = CalendarPresentation.date(from: "2026-10-14")!

  @Test("Reports today's chamber status and next return")
  func chamberStatuses() throws {
    let statuses = CalendarPresentation.statuses(in: CalendarSnapshot.sample.session, on: today)

    #expect(statuses.map(\.inSessionToday) == [true, true])
    #expect(statuses.map(\.line) == ["House: in session today", "Senate: in session today"])

    let recess = try #require(CalendarPresentation.date(from: "2026-10-17"))
    let recessStatuses = CalendarPresentation.statuses(
      in: CalendarSnapshot.sample.session,
      on: recess
    )
    #expect(recessStatuses[0].line == "House: on recess — returns Tue, Oct 20")
    #expect(recessStatuses[1].line == "Senate: on recess — returns Mon, Oct 19")
  }

  @Test("Filters upcoming elections by state while retaining the national general")
  func upcomingElectionScope() throws {
    let calendar = ElectionCalendar(
      generatedAt: "now",
      source: "source",
      elections: [
        ElectionEvent(state: "AZ", date: "2026-08-04", type: .primary, electionYear: 2026),
        ElectionEvent(state: "TX", date: "2026-10-20", type: .primaryRunoff, electionYear: 2026),
        ElectionEvent(state: "AZ", date: "2026-10-25", type: .primaryRunoff, electionYear: 2026),
        ElectionEvent(state: "US", date: "2026-11-03", type: .general, electionYear: 2026),
      ]
    )

    let upcoming = CalendarPresentation.upcomingElections(
      in: calendar,
      today: today,
      stateCode: "az"
    )

    #expect(upcoming.map(\.event.state) == ["AZ", "US"])
    #expect(upcoming.map(\.countdown) == ["in 11 days", "in 20 days"])
  }

  @Test("Groups nearby session days into readable blocks")
  func sessionBlocks() {
    let blocks = CalendarPresentation.sessionBlocks(
      from: ["2026-10-14", "2026-10-15", "2026-10-20", "2026-11-09"],
      today: today
    )

    #expect(
      blocks.map(\.rangeText) == [
        "Oct 14 – Oct 15 (2 session days)",
        "Oct 20 (1 session day)",
        "Nov 9 (1 session day)",
      ])
  }

  @Test("Presents verified registration deadlines without inventing missing data")
  func registrationDeadlines() {
    let deadline = RegistrationDeadline(
      online: "2026-10-13",
      byMail: "2026-10-20",
      inPerson: "not-a-date",
      sameDay: true
    )

    let presentation = CalendarPresentation.registrationDeadline(deadline, today: today)

    #expect(presentation?.headline == "Registration closes in 6 days (Oct 20)")
    #expect(presentation?.partialClosedText == "Online registration has closed.")
    #expect(presentation?.isUrgent == true)
    #expect(presentation?.sameDay == true)
    #expect(CalendarPresentation.registrationDeadline(nil, today: today) == nil)
  }

  @Test("Matches saved representatives only to dated general elections")
  func ballotRepresentatives() {
    let ballot = CalendarPresentation.ballotRepresentatives(
      Member.sampleSenators + [.sampleHouse],
      elections: CalendarSnapshot.sample.elections,
      today: today
    )

    #expect(ballot.map(\.member.bioguideID) == ["S001183"])
    #expect(ballot.first?.election.countdown == "in 20 days")
  }

  @Test("Uses canonical vote.gov state links and a safe national fallback")
  func voteGovLinks() {
    #expect(
      CalendarPresentation.voteGovURL(stateCode: "az").absoluteString
        == "https://vote.gov/register/arizona")
    #expect(
      CalendarPresentation.voteGovURL(stateCode: "DC").absoluteString
        == "https://vote.gov/register/district-of-columbia")
    #expect(CalendarPresentation.voteGovURL(stateCode: nil).absoluteString == "https://vote.gov/")
  }
}
