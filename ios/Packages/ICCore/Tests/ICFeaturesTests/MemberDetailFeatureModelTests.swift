import Dependencies
import ICClients
import ICFeatures
import ICModels
import Testing

@MainActor
@Suite("Member detail feature")
struct MemberDetailFeatureModelTests {
  @Test("Loads all injected profile shards and defaults to voting record")
  func loadsRecord() async {
    await withDependencies {
      $0.membersClient = .detailFixture
    } operation: {
      let model = MemberDetailFeatureModel(member: .sampleSenators[0])
      await model.load()

      guard case .loaded(let snapshot) = model.state else {
        Issue.record("Expected the member record to load")
        return
      }
      #expect(snapshot.sponsored.map(\.id) == ["s4478-119", "s2448-119"])
      #expect(snapshot.cosponsored.map(\.id) == ["hr1-119"])
      #expect(snapshot.recentVotes.count == 3)
      #expect(model.selectedTab == .votes)
    }
  }

  @Test("Caps recent votes at 25")
  func capsVotes() async {
    let rows = (1...40).map { index in
      MemberVoteRow(
        voteID: "vote-\(index)",
        congress: 119,
        date: "2026-09-22",
        question: "On Passage",
        result: "Passed",
        position: .yea
      )
    }
    await withDependencies {
      $0.membersClient = MembersClient(
        fetchCurrent: { .sample },
        fetchSponsored: { _ in .sampleSponsored },
        fetchCosponsored: { _ in .sampleCosponsored },
        fetchVotes: { id in
          MemberVotes(generatedAt: "now", bioguideID: id, voteCount: rows.count, votes: rows)
        }
      )
    } operation: {
      let model = MemberDetailFeatureModel(member: .sampleSenators[0])
      await model.load()
      guard case .loaded(let snapshot) = model.state else {
        Issue.record("Expected the member record to load")
        return
      }
      #expect(snapshot.recentVotes.count == 25)
      #expect(snapshot.recentVotes.last?.voteID == "vote-25")
    }
  }

  @Test("Search requires every normalized term to match a legislation field")
  func search() async {
    await withDependencies {
      $0.membersClient = .detailFixture
    } operation: {
      let model = MemberDetailFeatureModel(member: .sampleSenators[0])
      await model.load()
      model.selectedTab = .sponsored
      model.searchQuery = "S. 4478 worker"
      #expect(model.visibleLegislation.map(\.id) == ["s4478-119"])
      model.searchQuery = "military"
      #expect(model.visibleLegislation.map(\.id) == ["s2448-119"])
    }
  }

  @Test("Falls back to sponsored when no votes are published")
  func noVotes() async {
    await withDependencies {
      $0.membersClient = MembersClient(
        fetchCurrent: { .sample },
        fetchSponsored: { _ in .sampleSponsored },
        fetchCosponsored: { _ in .sampleCosponsored },
        fetchVotes: { id in
          MemberVotes(generatedAt: "now", bioguideID: id, voteCount: 0, votes: [])
        }
      )
    } operation: {
      let model = MemberDetailFeatureModel(member: .sampleSenators[0])
      await model.load()
      #expect(model.selectedTab == .sponsored)
    }
  }

  @Test("Surfaces friendly retry copy without leaking transport errors")
  func loadFailure() async {
    await withDependencies {
      $0.membersClient = MembersClient(
        fetchCurrent: { .sample },
        fetchSponsored: { _ in throw DetailTestError.offline },
        fetchCosponsored: { _ in .sampleCosponsored },
        fetchVotes: { _ in .sample }
      )
    } operation: {
      let model = MemberDetailFeatureModel(member: .sampleSenators[0])
      await model.load()
      guard case .failed(let message) = model.state else {
        Issue.record("Expected a friendly failure state")
        return
      }
      #expect(message.contains("Check your connection"))
      #expect(!message.contains("offline"))
    }
  }
}

private enum DetailTestError: Error {
  case offline
}

extension MembersClient {
  fileprivate static let detailFixture = MembersClient(
    fetchCurrent: { .sample },
    fetchSponsored: { _ in .sampleSponsored },
    fetchCosponsored: { _ in .sampleCosponsored },
    fetchVotes: { _ in .sample }
  )
}
