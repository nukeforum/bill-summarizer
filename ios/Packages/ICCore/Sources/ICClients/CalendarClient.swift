import Dependencies
import Foundation
import ICModels

public struct CalendarSnapshot: Equatable, Sendable {
  public let session: SessionCalendar
  public let elections: ElectionCalendar

  public init(session: SessionCalendar, elections: ElectionCalendar) {
    self.session = session
    self.elections = elections
  }
}

public struct CalendarClient: Sendable {
  public var fetch: @Sendable () async throws -> CalendarSnapshot

  public init(fetch: @escaping @Sendable () async throws -> CalendarSnapshot) {
    self.fetch = fetch
  }
}

extension CalendarClient: DependencyKey {
  public static let liveValue = CalendarClient {
    throw UnimplementedCalendarClientError()
  }

  public static let previewValue = CalendarClient {
    .sample
  }

  public static let testValue = CalendarClient {
    throw UnimplementedCalendarClientError()
  }
}

extension DependencyValues {
  public var calendarClient: CalendarClient {
    get { self[CalendarClient.self] }
    set { self[CalendarClient.self] = newValue }
  }
}

public struct UnimplementedCalendarClientError: Error, Equatable, Sendable {
  public init() {}
}

extension CalendarSnapshot {
  public static let sample = CalendarSnapshot(
    session: SessionCalendar(
      generatedAt: "2026-10-14T10:30:02Z",
      source: SessionCalendarSource(
        house: URL(string: "https://votingdays.house.gov/voting-days.ics")!,
        senate: URL(string: "https://www.senate.gov/legislative/2026_schedule.xml")!
      ),
      chambers: [
        "house": ChamberCalendar(
          sessionDays: [
            "2026-10-14", "2026-10-15", "2026-10-16", "2026-10-20", "2026-10-21",
            "2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12",
          ]
        ),
        "senate": ChamberCalendar(
          sessionDays: [
            "2026-10-14", "2026-10-15", "2026-10-19", "2026-10-20", "2026-10-21",
            "2026-11-09", "2026-11-10", "2026-11-12", "2026-11-13",
          ]
        ),
      ]
    ),
    elections: ElectionCalendar(
      generatedAt: "2026-10-12T12:19:27Z",
      source:
        "Federal general-election date is statutory; state dates are published from official sources.",
      elections: [
        ElectionEvent(
          state: "US",
          date: "2026-11-03",
          type: .general,
          electionYear: 2026
        )
      ]
    )
  )
}
