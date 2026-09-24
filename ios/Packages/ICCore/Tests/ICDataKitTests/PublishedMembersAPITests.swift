import Foundation
import ICDataKit
import ICModels
import Testing

@Suite("Published members API")
struct PublishedMembersAPITests {
  @Test("Fetches the current Congress members using the published paths")
  func fetchesCurrentMembers() async throws {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [MembersFixtureURLProtocol.self]
    let session = URLSession(configuration: configuration)
    defer { session.invalidateAndCancel() }

    let api = PublishedMembersAPI(
      baseURL: try #require(URL(string: "https://fixture.test/root/")),
      session: session
    )

    let index = try await api.fetchCurrentMembers()
    let senator = try #require(index.members.first)
    let representative = try #require(index.members.last)

    #expect(index.congress == 119)
    #expect(index.generatedAt == "2026-09-20T08:12:34Z")
    #expect(index.members.count == 3)
    #expect(senator.bioguideID == "K000377")
    #expect(senator.chamber == .senate)
    #expect(senator.contactForm?.host == "www.kelly.senate.gov")
    #expect(senator.socials == [SocialHandle(platform: "instagram", handle: "senmarkkelly")])
    #expect(representative.bioguideID == "S001183")
    #expect(representative.chamber == .house)
    #expect(representative.district == 1)
  }

  @Test("HTTP errors retain the response status")
  func errorContainsStatus() {
    #expect(PublishedMembersAPIError.httpStatus(503) == .httpStatus(503))
  }

  @Test("Fetches member legislation and vote shards")
  func fetchesMemberDetail() async throws {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [MembersFixtureURLProtocol.self]
    let session = URLSession(configuration: configuration)
    defer { session.invalidateAndCancel() }
    let api = PublishedMembersAPI(
      baseURL: try #require(URL(string: "https://fixture.test/root/")),
      session: session
    )

    async let sponsored = api.fetchSponsored(for: "K000377")
    async let cosponsored = api.fetchCosponsored(for: "K000377")
    async let votes = api.fetchVotes(for: "K000377")
    let results = try await (sponsored, cosponsored, votes)

    #expect(results.0.bills.count == 2)
    #expect(results.0.renderableBills.map(\.id) == ["s4478-119"])
    #expect(results.1.bills.map(\.id) == ["hr1-119"])
    #expect(results.2.voteCount == 2)
    #expect(results.2.votes.map(\.position) == [.nay, .yea])
    #expect(results.2.votes.last?.shortTitle == "Community Preparedness Act")
  }

  @Test("Treats missing member shards as empty published data")
  func missingShardsAreEmpty() async throws {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [MembersFixtureURLProtocol.self]
    let session = URLSession(configuration: configuration)
    defer { session.invalidateAndCancel() }
    let api = PublishedMembersAPI(
      baseURL: try #require(URL(string: "https://fixture.test/root/")),
      session: session
    )

    #expect(try await api.fetchSponsored(for: "MISSING").bills.isEmpty)
    #expect(try await api.fetchCosponsored(for: "MISSING").bills.isEmpty)
    #expect(try await api.fetchVotes(for: "MISSING").votes.isEmpty)
  }
}

private final class MembersFixtureURLProtocol: URLProtocol, @unchecked Sendable {
  private static let fixturesByPath = [
    "/root/data/congresses.json": "congresses",
    "/root/data/members_119.json": "members_119",
    "/root/data/members/K000377_sponsored.json": "K000377_sponsored",
    "/root/data/members/K000377_cosponsored.json": "K000377_cosponsored",
    "/root/data/votes/members/K000377.json": "K000377_votes",
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
      let response = try #require(
        HTTPURLResponse(
          url: url,
          statusCode: 200,
          httpVersion: "HTTP/1.1",
          headerFields: ["Content-Type": "application/json"]
        )
      )
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
