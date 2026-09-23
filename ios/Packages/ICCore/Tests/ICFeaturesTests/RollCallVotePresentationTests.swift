import ICFeatures
import ICModels
import Testing

@Suite("Roll-call vote presentation")
struct RollCallVotePresentationTests {
  @Test("Formats totals and party splits in a stable order")
  func formatting() {
    let presentation = RollCallVotePresentation(vote: .sample())

    #expect(presentation.chamberName == "House")
    #expect(presentation.resultIndicatesPassage)
    #expect(presentation.partySplitLine(for: "yea", label: "Yea") == "Yea — D 210 · R 8")
    #expect(
      presentation.accessibilityDescription
        == "House, 2026-09-17, On Passage, Bill Passed. 218 yea, 210 nay. "
        + "Yea: 210 Democrats, 8 Republicans. Nay: 210 Republicans. 7 not voting."
    )
  }

  @Test("Treats affirmative Congressional result phrases as passage")
  func passagePhrases() {
    #expect(
      RollCallVotePresentation(vote: .sample(result: "Joint Resolution Passed"))
        .resultIndicatesPassage)
    #expect(
      RollCallVotePresentation(vote: .sample(result: "Motion Agreed to")).resultIndicatesPassage)
    #expect(!RollCallVotePresentation(vote: .sample(result: "Rejected")).resultIndicatesPassage)
  }
}

extension RollCallVoteReference {
  fileprivate static func sample(result: String = "Bill Passed") -> Self {
    RollCallVoteReference(
      id: "house-119-1-17",
      chamber: .house,
      session: 1,
      rollNumber: 17,
      date: "2026-09-17",
      question: "On Passage",
      result: result,
      totals: VoteTotals(yea: 218, nay: 210, present: 0, notVoting: 7),
      partySplit: [
        "yea": ["R": 8, "D": 210],
        "nay": ["R": 210],
      ],
      path: "votes/congress119/house-1-17.json"
    )
  }
}
