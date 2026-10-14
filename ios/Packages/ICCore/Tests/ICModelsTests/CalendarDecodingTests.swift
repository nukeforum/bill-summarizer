import Foundation
import ICModels
import Testing

@Suite("Published calendar decoding")
struct CalendarDecodingTests {
  @Test("Decodes optional election details and unknown types safely")
  func decodesElectionCalendar() throws {
    let data = Data(
      """
      {
        "generated_at": "2026-10-12T12:19:27Z",
        "source": "Official sources",
        "elections": [{
          "state": "AZ",
          "date": "2026-11-03",
          "type": "future_kind",
          "election_year": 2026,
          "registration": {
            "online": "2026-10-05",
            "by_mail": "2026-10-05",
            "same_day": false,
            "source": "https://azsos.gov/elections"
          }
        }]
      }
      """.utf8
    )

    let calendar = try JSONDecoder().decode(ElectionCalendar.self, from: data)
    let event = try #require(calendar.elections.first)

    #expect(event.type == .unknown)
    #expect(event.electionYear == 2026)
    #expect(event.registration?.byMail == "2026-10-05")
    #expect(event.registration?.source?.host == "azsos.gov")
  }
}
