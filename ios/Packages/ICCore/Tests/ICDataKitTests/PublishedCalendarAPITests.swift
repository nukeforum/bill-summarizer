import Foundation
import ICDataKit
import Testing

@Suite("Published calendar API")
struct PublishedCalendarAPITests {
  @Test("Fetches both published calendar artifacts")
  func fetchesCalendar() async throws {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [CalendarFixtureURLProtocol.self]
    let session = URLSession(configuration: configuration)
    defer { session.invalidateAndCancel() }

    let api = PublishedCalendarAPI(
      baseURL: try #require(URL(string: "https://fixture.test/root/")),
      session: session
    )
    let snapshot = try await api.fetchCalendar()

    #expect(snapshot.session.chambers["house"]?.sessionDays == ["2026-10-14"])
    #expect(snapshot.session.source.senate.host == "senate.gov")
    #expect(snapshot.elections.elections.first?.date == "2026-11-03")
  }

  @Test("HTTP errors retain the response status")
  func errorContainsStatus() {
    #expect(PublishedCalendarAPIError.httpStatus(503) == .httpStatus(503))
  }
}

private final class CalendarFixtureURLProtocol: URLProtocol, @unchecked Sendable {
  override class func canInit(with request: URLRequest) -> Bool {
    request.url?.host == "fixture.test"
  }

  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

  override func startLoading() {
    guard let url = request.url else { return }
    let body: String
    switch url.lastPathComponent {
    case "session_calendar.json":
      body = """
        {
          "generated_at": "2026-10-14T10:30:02Z",
          "source": {
            "house": "https://house.gov/calendar",
            "senate": "https://senate.gov/calendar"
          },
          "chambers": {
            "house": {"session_days": ["2026-10-14"]},
            "senate": {"session_days": ["2026-10-15"]}
          }
        }
        """
    case "election_calendar.json":
      body = """
        {
          "generated_at": "2026-10-12T12:19:27Z",
          "source": "Official sources",
          "elections": [{
            "state": "US",
            "date": "2026-11-03",
            "type": "general",
            "election_year": 2026
          }]
        }
        """
    default:
      respond(status: 404, data: Data())
      return
    }
    respond(status: 200, data: Data(body.utf8))
  }

  override func stopLoading() {}

  private func respond(status: Int, data: Data) {
    guard
      let url = request.url,
      let response = HTTPURLResponse(
        url: url,
        statusCode: status,
        httpVersion: "HTTP/1.1",
        headerFields: ["Content-Type": "application/json"]
      )
    else { return }
    client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
    client?.urlProtocol(self, didLoad: data)
    client?.urlProtocolDidFinishLoading(self)
  }
}
