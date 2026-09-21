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
  }
}
