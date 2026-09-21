import Dependencies
import Foundation
import ICModels

public struct BillsSnapshot: Equatable, Sendable {
  public let generatedAt: String
  public let congress: Int
  public let votesCoverage: Bool
  public let bills: [Bill]

  public init(
    generatedAt: String,
    congress: Int,
    votesCoverage: Bool,
    bills: [Bill]
  ) {
    self.generatedAt = generatedAt
    self.congress = congress
    self.votesCoverage = votesCoverage
    self.bills = bills
  }
}

public struct BillsClient: Sendable {
  public var fetch: @Sendable () async throws -> BillsSnapshot

  public init(fetch: @escaping @Sendable () async throws -> BillsSnapshot) {
    self.fetch = fetch
  }
}

extension BillsClient: DependencyKey {
  public static let liveValue = BillsClient(fetch: {
    throw UnimplementedBillsClientError()
  })

  public static let previewValue = BillsClient(fetch: {
    BillsSnapshot(
      generatedAt: "2026-09-19T15:43:14Z",
      congress: 119,
      votesCoverage: true,
      bills: Bill.samples
    )
  })

  public static let testValue = BillsClient(fetch: {
    throw UnimplementedBillsClientError()
  })
}

extension DependencyValues {
  public var billsClient: BillsClient {
    get { self[BillsClient.self] }
    set { self[BillsClient.self] = newValue }
  }
}

public struct UnimplementedBillsClientError: Error, Equatable, Sendable {
  public init() {}
}
