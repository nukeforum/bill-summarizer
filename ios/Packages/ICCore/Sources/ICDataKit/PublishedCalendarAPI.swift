import Foundation
import ICClients
import ICModels

public actor PublishedCalendarAPI {
  private let baseURL: URL
  private let session: URLSession
  private let decoder = JSONDecoder()

  public init(
    baseURL: URL = PublishedBillsAPI.productionBaseURL,
    session: URLSession = URLSession(configuration: .default)
  ) {
    self.baseURL = baseURL
    self.session = session
  }

  public func fetchCalendar() async throws -> CalendarSnapshot {
    async let sessionCalendar: SessionCalendar = get(path: "data/session_calendar.json")
    async let electionCalendar: ElectionCalendar = get(path: "data/election_calendar.json")
    return try await CalendarSnapshot(session: sessionCalendar, elections: electionCalendar)
  }

  private func get<Response: Decodable & Sendable>(path: String) async throws -> Response {
    guard let url = URL(string: path, relativeTo: baseURL) else {
      throw PublishedCalendarAPIError.invalidURL(path)
    }

    var request = URLRequest(url: url)
    request.httpMethod = "GET"
    request.cachePolicy = .reloadRevalidatingCacheData
    request.timeoutInterval = 30

    let (data, response) = try await session.data(for: request)
    guard let httpResponse = response as? HTTPURLResponse else {
      throw PublishedCalendarAPIError.invalidResponse
    }
    guard (200..<300).contains(httpResponse.statusCode) else {
      throw PublishedCalendarAPIError.httpStatus(httpResponse.statusCode)
    }
    return try decoder.decode(Response.self, from: data)
  }
}

public enum PublishedCalendarAPIError: Error, Equatable, Sendable {
  case invalidURL(String)
  case invalidResponse
  case httpStatus(Int)
}

extension CalendarClient {
  public static func live(api: PublishedCalendarAPI = PublishedCalendarAPI()) -> Self {
    Self(fetch: { try await api.fetchCalendar() })
  }
}
