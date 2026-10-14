import Foundation

public struct SessionCalendar: Codable, Equatable, Sendable {
  public let generatedAt: String
  public let source: SessionCalendarSource
  public let chambers: [String: ChamberCalendar]

  public init(
    generatedAt: String,
    source: SessionCalendarSource,
    chambers: [String: ChamberCalendar]
  ) {
    self.generatedAt = generatedAt
    self.source = source
    self.chambers = chambers
  }

  private enum CodingKeys: String, CodingKey {
    case source, chambers
    case generatedAt = "generated_at"
  }
}

public struct SessionCalendarSource: Codable, Equatable, Sendable {
  public let house: URL
  public let senate: URL

  public init(house: URL, senate: URL) {
    self.house = house
    self.senate = senate
  }
}

public struct ChamberCalendar: Codable, Equatable, Sendable {
  public let sessionDays: [String]

  public init(sessionDays: [String]) {
    self.sessionDays = sessionDays
  }

  private enum CodingKeys: String, CodingKey {
    case sessionDays = "session_days"
  }
}

public struct ElectionCalendar: Codable, Equatable, Sendable {
  public let generatedAt: String
  public let source: String
  public let elections: [ElectionEvent]

  public init(generatedAt: String, source: String, elections: [ElectionEvent]) {
    self.generatedAt = generatedAt
    self.source = source
    self.elections = elections
  }

  private enum CodingKeys: String, CodingKey {
    case source, elections
    case generatedAt = "generated_at"
  }
}

public struct ElectionEvent: Codable, Equatable, Hashable, Identifiable, Sendable {
  public let state: String
  public let date: String
  public let type: ElectionType
  public let electionYear: Int
  public let source: String?
  public let registration: RegistrationDeadline?

  public var id: String { "\(state)-\(date)-\(type.rawValue)" }

  public init(
    state: String,
    date: String,
    type: ElectionType,
    electionYear: Int,
    source: String? = nil,
    registration: RegistrationDeadline? = nil
  ) {
    self.state = state
    self.date = date
    self.type = type
    self.electionYear = electionYear
    self.source = source
    self.registration = registration
  }

  private enum CodingKeys: String, CodingKey {
    case state, date, type, source, registration
    case electionYear = "election_year"
  }

  public init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    state = try container.decode(String.self, forKey: .state)
    date = try container.decode(String.self, forKey: .date)
    type = try container.decodeIfPresent(ElectionType.self, forKey: .type) ?? .unknown
    electionYear = try container.decode(Int.self, forKey: .electionYear)
    source = try container.decodeIfPresent(String.self, forKey: .source)
    registration = try container.decodeIfPresent(RegistrationDeadline.self, forKey: .registration)
  }
}

public enum ElectionType: String, Codable, Equatable, Hashable, Sendable {
  case general
  case primary
  case primaryRunoff = "primary_runoff"
  case unknown

  public init(from decoder: any Decoder) throws {
    let value = try decoder.singleValueContainer().decode(String.self)
    self = Self(rawValue: value) ?? .unknown
  }
}

public struct RegistrationDeadline: Codable, Equatable, Hashable, Sendable {
  public let online: String?
  public let byMail: String?
  public let inPerson: String?
  public let sameDay: Bool?
  public let source: URL?

  public init(
    online: String? = nil,
    byMail: String? = nil,
    inPerson: String? = nil,
    sameDay: Bool? = nil,
    source: URL? = nil
  ) {
    self.online = online
    self.byMail = byMail
    self.inPerson = inPerson
    self.sameDay = sameDay
    self.source = source
  }

  private enum CodingKeys: String, CodingKey {
    case online, source
    case byMail = "by_mail"
    case inPerson = "in_person"
    case sameDay = "same_day"
  }
}
