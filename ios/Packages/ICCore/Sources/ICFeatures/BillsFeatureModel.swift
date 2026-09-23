import Dependencies
import Foundation
import ICClients
import ICModels
import Observation

@Observable
@MainActor
public final class BillsFeatureModel {
  public enum State: Equatable, Sendable {
    case loading
    case loaded(BillsSnapshot)
    case failed(message: String)
  }

  public private(set) var state: State = .loading
  public var searchQuery = ""

  @ObservationIgnored
  @Dependency(\.billsClient) private var billsClient

  public init() {}

  public var visibleBills: [Bill] {
    guard case .loaded(let snapshot) = state else { return [] }
    let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !query.isEmpty else { return snapshot.bills }
    return snapshot.bills.filter { bill in
      [
        bill.displayNumber,
        bill.title,
        bill.shortTitle,
        bill.sponsor.name,
        bill.latestAction.text,
        bill.policyArea,
        bill.summaryCRS,
      ]
      .compactMap { $0 }
      .joined(separator: " ")
      .localizedCaseInsensitiveContains(query)
        || bill.subjects.contains(where: { $0.localizedCaseInsensitiveContains(query) })
    }
  }

  public var votesCoverage: Bool {
    guard case .loaded(let snapshot) = state else { return false }
    return snapshot.votesCoverage
  }

  public func load() async {
    state = .loading
    do {
      state = .loaded(try await billsClient.fetch())
    } catch is CancellationError {
      return
    } catch {
      state = .failed(message: "We couldn’t load bills. Check your connection and try again.")
    }
  }
}
