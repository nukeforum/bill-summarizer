import Dependencies
import Foundation
import ICClients
import ICModels
import Observation

public struct CalendarFeatureSnapshot: Equatable, Sendable {
  public let calendar: CalendarSnapshot
  public let stateCode: String?
  public let savedRepresentatives: [Member]

  public init(
    calendar: CalendarSnapshot,
    stateCode: String?,
    savedRepresentatives: [Member]
  ) {
    self.calendar = calendar
    self.stateCode = stateCode
    self.savedRepresentatives = savedRepresentatives
  }
}

@Observable
@MainActor
public final class CalendarFeatureModel {
  public enum State: Equatable, Sendable {
    case loading
    case loaded(CalendarFeatureSnapshot)
    case failed(message: String)
  }

  public private(set) var state: State = .loading
  public private(set) var today: Date = .distantPast

  @ObservationIgnored
  @Dependency(\.calendarClient) private var calendarClient

  @ObservationIgnored
  @Dependency(\.membersClient) private var membersClient

  @ObservationIgnored
  @Dependency(\.savedRepresentativesClient) private var savedRepresentativesClient

  @ObservationIgnored
  @Dependency(\.date.now) private var now

  public init() {
    today = CalendarPresentation.startOfDay(now)
  }

  public func load() async {
    state = .loading
    today = CalendarPresentation.startOfDay(now)
    do {
      async let savedSelection = try? savedRepresentativesClient.load()
      async let membersIndex = try? membersClient.fetchCurrent()
      let calendar = try await calendarClient.fetch()
      let (selection, members) = await (savedSelection, membersIndex)
      let savedRepresentatives =
        members?.members.filter {
          selection?.bioguideIDs.contains($0.bioguideID) == true
        } ?? []
      state = .loaded(
        CalendarFeatureSnapshot(
          calendar: calendar,
          stateCode: selection?.stateCode,
          savedRepresentatives: savedRepresentatives
        )
      )
    } catch is CancellationError {
      return
    } catch {
      state = .failed(
        message: "We couldn’t load the calendar. Check your connection and try again.")
    }
  }

  public func detailModel(for member: Member) -> MemberDetailFeatureModel {
    withDependencies {
      $0.membersClient = membersClient
    } operation: {
      MemberDetailFeatureModel(member: member)
    }
  }
}
