import Dependencies
import Foundation
import ICClients
import ICFeatures
import ICModels
import Testing

@MainActor
@Suite("Calendar feature")
struct CalendarFeatureModelTests {
  @Test("Loads injected calendars and saved location without global state")
  func loadsCalendar() async throws {
    let today = try #require(CalendarPresentation.date(from: "2026-10-14"))

    await withDependencies {
      $0.calendarClient = .previewValue
      $0.membersClient = .previewValue
      $0.savedRepresentativesClient = .previewValue
      $0.date = .constant(today)
    } operation: {
      let model = CalendarFeatureModel()
      await model.load()

      guard case .loaded(let snapshot) = model.state else {
        Issue.record("Expected calendar to load")
        return
      }
      #expect(model.today == today)
      #expect(snapshot.stateCode == "AZ")
      #expect(snapshot.savedRepresentatives.count == 3)
      #expect(snapshot.calendar.elections.elections.first?.type == .general)
    }
  }

  @Test("Surfaces a friendly retry state when the calendar request fails")
  func loadFailure() async {
    await withDependencies {
      $0.calendarClient = CalendarClient { throw CalendarFailure() }
      $0.membersClient = .previewValue
      $0.savedRepresentativesClient = .previewValue
      $0.date = .constant(Date(timeIntervalSinceReferenceDate: 0))
    } operation: {
      let model = CalendarFeatureModel()
      await model.load()

      #expect(
        model.state
          == .failed(message: "We couldn’t load the calendar. Check your connection and try again.")
      )
    }
  }
}

private struct CalendarFailure: Error {}
