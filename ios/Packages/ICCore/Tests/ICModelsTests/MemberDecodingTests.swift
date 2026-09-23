import Foundation
import ICModels
import Testing

@Suite("Published member decoding")
struct MemberDecodingTests {
  @Test("Unknown chambers and newly optional fields decode safely")
  func forwardCompatibleDefaults() throws {
    let data = try #require(
      """
      {
        "bioguide_id": "X000001",
        "name": "Future Member",
        "party": "I",
        "state": "DC",
        "district": 0,
        "chamber": "assembly"
      }
      """.data(using: .utf8)
    )

    let member = try JSONDecoder().decode(Member.self, from: data)

    #expect(member.chamber == .unknown)
    #expect(member.district == 0)
    #expect(member.sponsoredCount == 0)
    #expect(member.cosponsoredCount == 0)
    #expect(member.socials.isEmpty)
  }
}
