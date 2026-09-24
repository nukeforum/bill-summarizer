import Foundation
import ICClients
import ICModels

public actor PublishedMembersAPI {
  private let baseURL: URL
  private let session: URLSession
  private let decoder: JSONDecoder

  public init(
    baseURL: URL = PublishedBillsAPI.productionBaseURL,
    session: URLSession = URLSession(configuration: .default)
  ) {
    self.baseURL = baseURL
    self.session = session
    decoder = JSONDecoder()
  }

  public func fetchCurrentMembers() async throws -> MembersIndex {
    let congresses: CongressesIndex = try await get(path: "data/congresses.json")
    return try await get(path: "data/members_\(congresses.currentCongress).json")
  }

  public func fetchSponsored(for bioguideID: String) async throws -> MemberLegislation {
    try await getOrEmptyLegislation(
      path: "data/members/\(bioguideID)_sponsored.json",
      bioguideID: bioguideID,
      kind: "sponsored"
    )
  }

  public func fetchCosponsored(for bioguideID: String) async throws -> MemberLegislation {
    try await getOrEmptyLegislation(
      path: "data/members/\(bioguideID)_cosponsored.json",
      bioguideID: bioguideID,
      kind: "cosponsored"
    )
  }

  public func fetchVotes(for bioguideID: String) async throws -> MemberVotes {
    do {
      return try await get(path: "data/votes/members/\(bioguideID).json")
    } catch PublishedMembersAPIError.httpStatus(404) {
      return MemberVotes(generatedAt: "", bioguideID: bioguideID, voteCount: 0, votes: [])
    }
  }

  private func getOrEmptyLegislation(
    path: String,
    bioguideID: String,
    kind: String
  ) async throws -> MemberLegislation {
    do {
      return try await get(path: path)
    } catch PublishedMembersAPIError.httpStatus(404) {
      return MemberLegislation(
        bioguideID: bioguideID,
        congress: 0,
        kind: kind,
        generatedAt: "",
        bills: []
      )
    }
  }

  private func get<Response: Decodable & Sendable>(path: String) async throws -> Response {
    guard let url = URL(string: path, relativeTo: baseURL) else {
      throw PublishedMembersAPIError.invalidURL(path)
    }

    var request = URLRequest(url: url)
    request.httpMethod = "GET"
    request.cachePolicy = .reloadRevalidatingCacheData
    request.timeoutInterval = 30

    let (data, response) = try await session.data(for: request)
    guard let httpResponse = response as? HTTPURLResponse else {
      throw PublishedMembersAPIError.invalidResponse
    }
    guard (200..<300).contains(httpResponse.statusCode) else {
      throw PublishedMembersAPIError.httpStatus(httpResponse.statusCode)
    }
    return try decoder.decode(Response.self, from: data)
  }
}

public enum PublishedMembersAPIError: Error, Equatable, Sendable {
  case invalidURL(String)
  case invalidResponse
  case httpStatus(Int)
}

extension MembersClient {
  public static func live(api: PublishedMembersAPI = PublishedMembersAPI()) -> Self {
    Self(
      fetchCurrent: { try await api.fetchCurrentMembers() },
      fetchSponsored: { try await api.fetchSponsored(for: $0) },
      fetchCosponsored: { try await api.fetchCosponsored(for: $0) },
      fetchVotes: { try await api.fetchVotes(for: $0) }
    )
  }
}
