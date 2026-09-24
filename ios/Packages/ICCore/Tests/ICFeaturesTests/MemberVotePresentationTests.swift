import Foundation
import ICFeatures
import ICModels
import Testing

@Suite("Member vote presentation")
struct MemberVotePresentationTests {
  @Test("Formats positions, dates, and accessibility copy")
  func formatsVote() {
    let vote = MemberVoteRow(
      voteID: "senate-119-2-228",
      congress: 119,
      date: "2026-08-08",
      question: "On Passage of the Bill",
      result: "Bill Passed",
      position: .yea,
      billID: "hr6500-119",
      type: "hr",
      number: "6500",
      shortTitle: "Community Preparedness Act"
    )
    let presentation = MemberVotePresentation(vote: vote)

    #expect(presentation.positionLabel == "Yea")
    #expect(presentation.formattedDate == "Aug 8, 2026")
    #expect(vote.billLabel == "HR 6500")
    #expect(presentation.accessibilityDescription.contains("Voted yea"))
  }

  @Test("Falls back safely for future positions and malformed dates")
  func forwardCompatibleFallbacks() throws {
    let data = try #require(
      """
      {
        "vote_id": "future-vote",
        "congress": 119,
        "date": "someday",
        "question": "Future question",
        "result": "Pending",
        "position": "paired"
      }
      """.data(using: .utf8)
    )
    let vote = try JSONDecoder().decode(MemberVoteRow.self, from: data)
    let presentation = MemberVotePresentation(vote: vote)

    #expect(vote.position == .unknown)
    #expect(presentation.positionLabel == "Unknown")
    #expect(presentation.formattedDate == "someday")
  }
}
