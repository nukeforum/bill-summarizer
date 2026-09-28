import Dependencies
import Foundation
import ICClients
import ICModels
import Observation

public struct MemberDetailSnapshot: Equatable, Sendable {
  public let member: Member
  public let sponsored: [MemberLegislationItem]
  public let cosponsored: [MemberLegislationItem]
  public let recentVotes: [MemberVoteRow]

  public init(
    member: Member,
    sponsored: [MemberLegislationItem],
    cosponsored: [MemberLegislationItem],
    recentVotes: [MemberVoteRow]
  ) {
    self.member = member
    self.sponsored = sponsored
    self.cosponsored = cosponsored
    self.recentVotes = recentVotes
  }
}

@Observable
@MainActor
public final class MemberDetailFeatureModel {
  public enum State: Equatable, Sendable {
    case loading
    case loaded(MemberDetailSnapshot)
    case failed(message: String)
  }

  public enum Tab: String, CaseIterable, Sendable {
    case votes = "Voting record"
    case sponsored = "Sponsored"
    case cosponsored = "Cosponsored"
  }

  public let member: Member
  public private(set) var state: State = .loading
  public var selectedTab: Tab = .votes
  public var searchQuery = ""

  @ObservationIgnored
  @Dependency(\.membersClient) private var membersClient

  public init(member: Member) {
    self.member = member
  }

  public var visibleLegislation: [MemberLegislationItem] {
    guard case .loaded(let snapshot) = state else { return [] }
    let source = selectedTab == .sponsored ? snapshot.sponsored : snapshot.cosponsored
    let terms =
      searchQuery
      .split(whereSeparator: \.isWhitespace)
      .map { $0.replacingOccurrences(of: ".", with: "") }
      .filter { !$0.isEmpty }
    guard !terms.isEmpty else { return source }
    return source.filter { item in
      let fields = [
        item.title,
        item.policyArea,
        item.latestAction.text,
        "\(item.type)\(item.number)",
        "\(item.type) \(item.number)",
      ].compactMap { $0 }
      return terms.allSatisfy { term in
        fields.contains { $0.localizedCaseInsensitiveContains(term) }
      }
    }
  }

  public func load() async {
    state = .loading
    do {
      async let sponsored = membersClient.fetchSponsored(member.bioguideID)
      async let cosponsored = membersClient.fetchCosponsored(member.bioguideID)
      async let votes = membersClient.fetchVotes(member.bioguideID)
      let (sponsoredResult, cosponsoredResult, votesResult) =
        try await (sponsored, cosponsored, votes)
      let snapshot = MemberDetailSnapshot(
        member: member,
        sponsored: sponsoredResult.renderableBills,
        cosponsored: cosponsoredResult.renderableBills,
        recentVotes: Array(votesResult.votes.prefix(25))
      )
      state = .loaded(snapshot)
      selectedTab = snapshot.recentVotes.isEmpty ? .sponsored : .votes
    } catch is CancellationError {
      return
    } catch {
      state = .failed(
        message:
          "We couldn’t load this representative’s record. Check your connection and try again."
      )
    }
  }
}
