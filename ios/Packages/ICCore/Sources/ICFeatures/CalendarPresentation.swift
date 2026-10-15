import Foundation
import ICModels

public enum LegislativeChamber: String, CaseIterable, Equatable, Sendable {
  case house
  case senate

  public var displayName: String {
    switch self {
    case .house: "House"
    case .senate: "Senate"
    }
  }
}

public struct ChamberSessionStatus: Equatable, Sendable {
  public let chamber: LegislativeChamber
  public let inSessionToday: Bool
  public let nextSessionDay: Date?

  public init(
    chamber: LegislativeChamber,
    inSessionToday: Bool,
    nextSessionDay: Date?
  ) {
    self.chamber = chamber
    self.inSessionToday = inSessionToday
    self.nextSessionDay = nextSessionDay
  }

  public var line: String {
    if inSessionToday {
      return "\(chamber.displayName): in session today"
    }
    if let nextSessionDay {
      return "\(chamber.displayName): on recess — returns \(nextSessionDay.formattedShortWeekday)"
    }
    return "\(chamber.displayName): session has ended"
  }
}

public struct UpcomingElection: Equatable, Identifiable, Sendable {
  public let event: ElectionEvent
  public let date: Date
  public let daysUntil: Int

  public var id: String { event.id }
  public var isNationwide: Bool { event.state == "US" }

  public var label: String {
    let type =
      switch event.type {
      case .general: "General election"
      case .primary: "Primary"
      case .primaryRunoff: "Primary runoff"
      case .unknown: "Election"
      }
    return isNationwide ? type : "\(event.state) \(type.lowercased())"
  }

  public var countdown: String {
    switch daysUntil {
    case 0: "Today"
    case 1: "Tomorrow"
    default: "in \(daysUntil) days"
    }
  }
}

public struct SessionBlock: Equatable, Identifiable, Sendable {
  public let start: Date
  public let end: Date
  public let dayCount: Int

  public var id: String {
    "\(start.timeIntervalSinceReferenceDate)-\(end.timeIntervalSinceReferenceDate)"
  }

  public var rangeText: String {
    let count = dayCount == 1 ? "1 session day" : "\(dayCount) session days"
    if start == end {
      return "\(start.formattedMonthDay) (\(count))"
    }
    return "\(start.formattedMonthDay) – \(end.formattedMonthDay) (\(count))"
  }
}

public struct BallotRepresentative: Equatable, Identifiable, Sendable {
  public let member: Member
  public let election: UpcomingElection

  public var id: String { member.id }
}

public enum RegistrationMethod: String, CaseIterable, Equatable, Sendable {
  case online = "Online"
  case byMail = "By mail"
  case inPerson = "In person"
}

public struct RegistrationMethodDeadline: Equatable, Identifiable, Sendable {
  public let method: RegistrationMethod
  public let date: Date
  public let daysUntil: Int

  public var id: RegistrationMethod { method }
}

public struct RegistrationDeadlinePresentation: Equatable, Sendable {
  public enum Status: Equatable, Sendable {
    case open(headline: RegistrationMethodDeadline, closed: [RegistrationMethod])
    case closed([RegistrationMethod])
  }

  public let status: Status
  public let methods: [RegistrationMethodDeadline]
  public let sameDay: Bool

  public var isUrgent: Bool {
    guard case .open(let headline, _) = status else { return false }
    return headline.daysUntil <= 7
  }

  public var headline: String {
    switch status {
    case .open(let deadline, _):
      let lead =
        switch deadline.daysUntil {
        case 0: "Registration closes today"
        case 1: "Registration closes in 1 day"
        default: "Registration closes in \(deadline.daysUntil) days"
        }
      return "\(lead) (\(deadline.date.formattedMonthDay))"
    case .closed(let methods):
      return "\(methodList(methods).capitalized) registration has closed for this election."
    }
  }

  public var partialClosedText: String? {
    guard case .open(_, let closed) = status, !closed.isEmpty else { return nil }
    return "\(methodList(closed).capitalized) registration has closed."
  }
}

public enum CalendarPresentation {
  public static func date(from value: String) -> Date? {
    let parts = value.split(separator: "-").compactMap { Int($0) }
    guard parts.count == 3 else { return nil }
    var calendar = Foundation.Calendar(identifier: .gregorian)
    calendar.timeZone = .current
    return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
  }

  public static func startOfDay(_ date: Date) -> Date {
    var calendar = Foundation.Calendar(identifier: .gregorian)
    calendar.timeZone = .current
    return calendar.startOfDay(for: date)
  }

  public static func statuses(
    in calendar: SessionCalendar,
    on today: Date
  ) -> [ChamberSessionStatus] {
    let civilToday = startOfDay(today)
    return LegislativeChamber.allCases.map { chamber in
      let days =
        calendar.chambers[chamber.rawValue]?.sessionDays.compactMap(date(from:)).sorted()
        ?? []
      return ChamberSessionStatus(
        chamber: chamber,
        inSessionToday: days.contains(civilToday),
        nextSessionDay: days.first(where: { $0 > civilToday })
      )
    }
  }

  public static func upcomingElections(
    in calendar: ElectionCalendar,
    today: Date,
    stateCode: String?,
    limit: Int = 5
  ) -> [UpcomingElection] {
    let today = startOfDay(today)
    let normalizedState = stateCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    return calendar.elections.compactMap { event in
      guard event.state == "US" || event.state.uppercased() == normalizedState else { return nil }
      guard let date = date(from: event.date) else { return nil }
      let days = dayDistance(from: today, to: date)
      guard days >= 0 else { return nil }
      return UpcomingElection(event: event, date: date, daysUntil: days)
    }
    .sorted { ($0.date, $0.event.state) < ($1.date, $1.event.state) }
    .prefix(limit)
    .map { $0 }
  }

  public static func sessionBlocks(
    from sessionDays: [String],
    today: Date,
    limit: Int = 3
  ) -> [SessionBlock] {
    let today = startOfDay(today)
    let days = sessionDays.compactMap(date(from:)).filter { $0 >= today }.sorted()
    guard let first = days.first else { return [] }

    var blocks: [SessionBlock] = []
    var blockStart = first
    var blockEnd = first
    var count = 1
    for day in days.dropFirst() {
      if dayDistance(from: blockEnd, to: day) <= 3 {
        blockEnd = day
        count += 1
      } else {
        blocks.append(SessionBlock(start: blockStart, end: blockEnd, dayCount: count))
        guard blocks.count < limit else { return blocks }
        blockStart = day
        blockEnd = day
        count = 1
      }
    }
    blocks.append(SessionBlock(start: blockStart, end: blockEnd, dayCount: count))
    return Array(blocks.prefix(limit))
  }

  public static func ballotRepresentatives(
    _ members: [Member],
    elections: ElectionCalendar,
    today: Date
  ) -> [BallotRepresentative] {
    members.compactMap { member in
      guard let year = member.nextElectionYear else { return nil }
      let election = upcomingElections(
        in: elections, today: today, stateCode: member.state, limit: 20
      )
      .first { $0.event.type == .general && $0.event.electionYear == year }
      return election.map { BallotRepresentative(member: member, election: $0) }
    }
    .sorted { ($0.election.date, $0.member.name) < ($1.election.date, $1.member.name) }
  }

  public static func registrationDeadline(
    _ deadline: RegistrationDeadline?,
    today: Date
  ) -> RegistrationDeadlinePresentation? {
    guard let deadline else { return nil }
    let today = startOfDay(today)
    let values: [(RegistrationMethod, String?)] = [
      (.online, deadline.online),
      (.byMail, deadline.byMail),
      (.inPerson, deadline.inPerson),
    ]
    let methods = values.compactMap { method, value -> RegistrationMethodDeadline? in
      guard let value, let date = date(from: value) else { return nil }
      return RegistrationMethodDeadline(
        method: method,
        date: date,
        daysUntil: dayDistance(from: today, to: date)
      )
    }
    .sorted { ($0.date, $0.method.rawValue) < ($1.date, $1.method.rawValue) }
    guard !methods.isEmpty else { return nil }

    let open = methods.filter { $0.daysUntil >= 0 }
    let closed = methods.filter { $0.daysUntil < 0 }.map(\.method)
    let status: RegistrationDeadlinePresentation.Status =
      if let headline = open.first {
        .open(headline: headline, closed: closed)
      } else {
        .closed(methods.map(\.method))
      }
    return RegistrationDeadlinePresentation(
      status: status,
      methods: methods,
      sameDay: deadline.sameDay == true
    )
  }

  public static func voteGovURL(stateCode: String?) -> URL {
    guard let slug = voteGovSlugs[stateCode?.uppercased() ?? ""] else {
      return URL(string: "https://vote.gov/")!
    }
    return URL(string: "https://vote.gov/register/\(slug)")!
  }

  private static func dayDistance(from start: Date, to end: Date) -> Int {
    var calendar = Foundation.Calendar(identifier: .gregorian)
    calendar.timeZone = .current
    return calendar.dateComponents([.day], from: start, to: end).day ?? 0
  }
}

private let voteGovSlugs = [
  "AL": "alabama", "AK": "alaska", "AZ": "arizona", "AR": "arkansas",
  "CA": "california", "CO": "colorado", "CT": "connecticut", "DE": "delaware",
  "FL": "florida", "GA": "georgia", "HI": "hawaii", "ID": "idaho",
  "IL": "illinois", "IN": "indiana", "IA": "iowa", "KS": "kansas",
  "KY": "kentucky", "LA": "louisiana", "ME": "maine", "MD": "maryland",
  "MA": "massachusetts", "MI": "michigan", "MN": "minnesota", "MS": "mississippi",
  "MO": "missouri", "MT": "montana", "NE": "nebraska", "NV": "nevada",
  "NH": "new-hampshire", "NJ": "new-jersey", "NM": "new-mexico", "NY": "new-york",
  "NC": "north-carolina", "ND": "north-dakota", "OH": "ohio", "OK": "oklahoma",
  "OR": "oregon", "PA": "pennsylvania", "RI": "rhode-island", "SC": "south-carolina",
  "SD": "south-dakota", "TN": "tennessee", "TX": "texas", "UT": "utah",
  "VT": "vermont", "VA": "virginia", "WA": "washington", "WV": "west-virginia",
  "WI": "wisconsin", "WY": "wyoming", "DC": "district-of-columbia",
  "AS": "american-samoa", "GU": "guam", "MP": "northern-mariana-islands",
  "PR": "puerto-rico", "VI": "virgin-islands",
]

private func methodList(_ methods: [RegistrationMethod]) -> String {
  let values = methods.map { $0.rawValue.lowercased() }
  switch values.count {
  case 0: return ""
  case 1: return values[0]
  case 2: return "\(values[0]) and \(values[1])"
  default: return "\(values.dropLast().joined(separator: ", ")), and \(values.last!)"
  }
}

extension Date {
  public var formattedFullDate: String {
    formatted(
      .dateTime.weekday(.wide).month(.wide).day().year().locale(Locale(identifier: "en_US")))
  }

  public var formattedShortWeekday: String {
    formatted(
      .dateTime.weekday(.abbreviated).month(.abbreviated).day().locale(
        Locale(identifier: "en_US")))
  }

  public var formattedMonthDay: String {
    formatted(.dateTime.month(.abbreviated).day().locale(Locale(identifier: "en_US")))
  }
}
