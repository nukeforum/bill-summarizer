import Foundation
import ICModels
import Testing

@Suite("Published bill decoding")
struct BillDecodingTests {
  @Test("Unknown enum values decode safely")
  func unknownValues() throws {
    let data = Data(
      """
      {
        "generated_at": "2026-09-19T15:43:14Z",
        "congress": 119,
        "bills": [{
          "id": "hr-1-119",
          "congress": 119,
          "type": "hr",
          "number": "1",
          "title": "Example",
          "sponsor": {"name": "Jordan Lee", "party": "I", "state": "AZ"},
          "introduced_date": "2026-01-08",
          "latest_action": {"date": "2026-09-17", "text": "Action"},
          "outcome": "future_outcome",
          "status": "future_status",
          "congress_gov_url": "https://www.congress.gov/bill/119th-congress/house-bill/1"
        }]
      }
      """.utf8
    )

    let manifest = try JSONDecoder().decode(BillsManifest.self, from: data)

    #expect(manifest.votesCoverage == false)
    #expect(manifest.bills.first?.outcome == .unknown)
    #expect(manifest.bills.first?.lifecycleStatus == .unknown)
    #expect(manifest.bills.first?.votes.isEmpty == true)
  }

  @Test("Unknown vote chambers decode safely")
  func unknownVoteChamber() throws {
    let data = Data(
      """
      {
        "id": "future-119-1-1",
        "chamber": "joint",
        "session": 1,
        "roll_number": 1,
        "date": "2026-09-22",
        "question": "On the Question",
        "result": "Agreed to",
        "totals": {"yea": 1, "nay": 0, "present": 0, "not_voting": 0},
        "path": "votes/congress119/future-1-1.json"
      }
      """.utf8
    )

    let vote = try JSONDecoder().decode(RollCallVoteReference.self, from: data)

    #expect(vote.chamber == .unknown)
    #expect(vote.partySplit.isEmpty)
  }
}
