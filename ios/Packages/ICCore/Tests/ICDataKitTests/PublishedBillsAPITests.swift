import Foundation
import ICDataKit
import ICModels
import Testing

@Suite("Published bills API")
struct PublishedBillsAPITests {
  @Test("Decodes the frozen congresses index contract")
  func decodesCongressesIndex() throws {
    let index = try JSONDecoder().decode(
      CongressesIndex.self,
      from: try Fixture.data(named: "congresses")
    )
    let entry = try #require(index.congresses.first)

    #expect(index.generatedAt == "2026-09-19T15:43:14Z")
    #expect(index.currentCongress == 119)
    #expect(entry.congress == 119)
    #expect(entry.billCount == 1)
    #expect(entry.manifestPath == "congress119_bills.json")
    #expect(entry.shardIndexPath == "congress119_bills_index.json")
  }

  @Test("Fetches the published paths and decodes the production wire contract")
  func fetchesPublishedSnapshot() async throws {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [FixtureURLProtocol.self]
    let session = URLSession(configuration: configuration)
    defer { session.invalidateAndCancel() }

    let api = PublishedBillsAPI(
      baseURL: try #require(URL(string: "https://fixture.test/root/")),
      session: session
    )

    let snapshot = try await api.fetchCurrentBills()
    let bill = try #require(snapshot.bills.first)

    #expect(snapshot.generatedAt == "2026-09-19T11:21:22Z")
    #expect(snapshot.congress == 119)
    #expect(snapshot.votesCoverage)
    #expect(snapshot.bills.count == 1)
    #expect(bill.id == "s307-119")
    #expect(bill.displayNumber == "S. 307")
    #expect(bill.outcome == .passedHouse)
    #expect(bill.policyArea == "Crime and Law Enforcement")
    #expect(bill.subjects == ["Congressional oversight", "Department of Justice"])
    let vote = try #require(bill.votes.first)
    #expect(vote.id == "house-119-2-284")
    #expect(vote.chamber == .house)
    #expect(vote.rollNumber == 284)
    #expect(vote.totals.yea == 376)
    #expect(vote.totals.notVoting == 46)
    #expect(vote.partySplit["yea"] == ["D": 189, "R": 187])
  }

  @Test("HTTP errors retain the response status")
  func errorContainsStatus() {
    #expect(PublishedBillsAPIError.httpStatus(503) == .httpStatus(503))
  }
}

private final class FixtureURLProtocol: URLProtocol, @unchecked Sendable {
  private static let fixturesByPath = [
    "/root/data/congresses.json": "congresses",
    "/root/data/congress119_bills.json": "congress119_bills",
  ]

  override class func canInit(with request: URLRequest) -> Bool {
    request.url?.host == "fixture.test"
  }

  override class func canonicalRequest(for request: URLRequest) -> URLRequest {
    request
  }

  override func startLoading() {
    guard
      let url = request.url,
      let fixtureName = Self.fixturesByPath[url.path],
      let fixtureURL = Bundle.module.url(forResource: fixtureName, withExtension: "json")
    else {
      respondWithStatus(404)
      return
    }

    do {
      let data = try Data(contentsOf: fixtureURL)
      guard
        let response = HTTPURLResponse(
          url: url,
          statusCode: 200,
          httpVersion: "HTTP/1.1",
          headerFields: ["Content-Type": "application/json"]
        )
      else {
        throw URLError(.badServerResponse)
      }
      client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
      client?.urlProtocol(self, didLoad: data)
      client?.urlProtocolDidFinishLoading(self)
    } catch {
      client?.urlProtocol(self, didFailWithError: error)
    }
  }

  override func stopLoading() {}

  private func respondWithStatus(_ statusCode: Int) {
    guard
      let url = request.url,
      let response = HTTPURLResponse(
        url: url,
        statusCode: statusCode,
        httpVersion: "HTTP/1.1",
        headerFields: nil
      )
    else {
      client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
      return
    }
    client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
    client?.urlProtocolDidFinishLoading(self)
  }
}

private enum Fixture {
  static func data(named name: String) throws -> Data {
    guard let url = Bundle.module.url(forResource: name, withExtension: "json") else {
      throw CocoaError(.fileNoSuchFile)
    }
    return try Data(contentsOf: url)
  }
}
