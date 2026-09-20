import Foundation
import ICClients
import ICModels

public actor PublishedBillsAPI {
  public static let productionBaseURL = URL(
    string: "https://nukeforum.github.io/bill-summarizer/"
  )!

  private let baseURL: URL
  private let session: URLSession
  private let decoder: JSONDecoder

  public init(
    baseURL: URL = productionBaseURL,
    session: URLSession = URLSession(configuration: .default)
  ) {
    self.baseURL = baseURL
    self.session = session
    decoder = JSONDecoder()
  }

  public func fetchCurrentBills() async throws -> BillsSnapshot {
    let index: CongressesIndex = try await get(path: "data/congresses.json")
    guard let entry = index.congresses.first(where: { $0.congress == index.currentCongress }) else {
      throw PublishedBillsAPIError.currentCongressMissing(index.currentCongress)
    }

    let manifest: BillsManifest = try await get(path: "data/\(entry.manifestPath)")
    return BillsSnapshot(
      generatedAt: manifest.generatedAt,
      congress: manifest.congress,
      votesCoverage: manifest.votesCoverage,
      bills: manifest.bills
    )
  }

  private func get<Response: Decodable & Sendable>(path: String) async throws -> Response {
    guard let url = URL(string: path, relativeTo: baseURL) else {
      throw PublishedBillsAPIError.invalidURL(path)
    }

    var request = URLRequest(url: url)
    request.httpMethod = "GET"
    request.cachePolicy = .reloadRevalidatingCacheData
    request.timeoutInterval = 30

    let (data, response) = try await session.data(for: request)
    guard let httpResponse = response as? HTTPURLResponse else {
      throw PublishedBillsAPIError.invalidResponse
    }
    guard (200..<300).contains(httpResponse.statusCode) else {
      throw PublishedBillsAPIError.httpStatus(httpResponse.statusCode)
    }
    return try decoder.decode(Response.self, from: data)
  }
}

public enum PublishedBillsAPIError: Error, Equatable, Sendable {
  case invalidURL(String)
  case invalidResponse
  case httpStatus(Int)
  case currentCongressMissing(Int)
}

extension BillsClient {
  public static func live(api: PublishedBillsAPI = PublishedBillsAPI()) -> Self {
    Self(fetch: { try await api.fetchCurrentBills() })
  }
}
