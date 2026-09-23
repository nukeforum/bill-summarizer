import Foundation

public struct BillsManifest: Codable, Equatable, Sendable {
  public let generatedAt: String
  public let congress: Int
  public let votesCoverage: Bool
  public let bills: [Bill]

  public init(
    generatedAt: String,
    congress: Int,
    votesCoverage: Bool = false,
    bills: [Bill]
  ) {
    self.generatedAt = generatedAt
    self.congress = congress
    self.votesCoverage = votesCoverage
    self.bills = bills
  }

  private enum CodingKeys: String, CodingKey {
    case generatedAt = "generated_at"
    case congress
    case votesCoverage = "votes_coverage"
    case bills
  }

  public init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    generatedAt = try container.decode(String.self, forKey: .generatedAt)
    congress = try container.decode(Int.self, forKey: .congress)
    votesCoverage = try container.decodeIfPresent(Bool.self, forKey: .votesCoverage) ?? false
    bills = try container.decode([Bill].self, forKey: .bills)
  }
}

public struct Bill: Codable, Identifiable, Hashable, Sendable {
  public let id: String
  public let congress: Int
  public let type: String
  public let number: String
  public let title: String
  public let shortTitle: String?
  public let sponsor: Sponsor
  public let introducedDate: String
  public let latestAction: LegislativeAction
  public let outcome: BillOutcome
  public let lifecycleStatus: LifecycleStatus?
  public let policyArea: String?
  public let subjects: [String]
  public let summaryCRS: String?
  public let textURLHTML: URL?
  public let congressURL: URL
  public let votes: [RollCallVoteReference]

  public init(
    id: String,
    congress: Int,
    type: String,
    number: String,
    title: String,
    shortTitle: String? = nil,
    sponsor: Sponsor,
    introducedDate: String,
    latestAction: LegislativeAction,
    outcome: BillOutcome,
    lifecycleStatus: LifecycleStatus? = nil,
    policyArea: String? = nil,
    subjects: [String] = [],
    summaryCRS: String? = nil,
    textURLHTML: URL? = nil,
    congressURL: URL,
    votes: [RollCallVoteReference] = []
  ) {
    self.id = id
    self.congress = congress
    self.type = type
    self.number = number
    self.title = title
    self.shortTitle = shortTitle
    self.sponsor = sponsor
    self.introducedDate = introducedDate
    self.latestAction = latestAction
    self.outcome = outcome
    self.lifecycleStatus = lifecycleStatus
    self.policyArea = policyArea
    self.subjects = subjects
    self.summaryCRS = summaryCRS
    self.textURLHTML = textURLHTML
    self.congressURL = congressURL
    self.votes = votes
  }

  public var displayNumber: String {
    let normalizedType =
      switch type.lowercased() {
      case "hr": "H.R."
      case "s": "S."
      case "hres": "H.Res."
      case "sres": "S.Res."
      case "hjres": "H.J.Res."
      case "sjres": "S.J.Res."
      case "hconres": "H.Con.Res."
      case "sconres": "S.Con.Res."
      default: type.uppercased()
      }
    return "\(normalizedType) \(number)"
  }

  private enum CodingKeys: String, CodingKey {
    case id, congress, type, number, title, sponsor, outcome, subjects, votes
    case shortTitle = "short_title"
    case introducedDate = "introduced_date"
    case latestAction = "latest_action"
    case lifecycleStatus = "status"
    case policyArea = "policy_area"
    case summaryCRS = "summary_crs"
    case textURLHTML = "text_url_html"
    case congressURL = "congress_gov_url"
  }

  public init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    id = try container.decode(String.self, forKey: .id)
    congress = try container.decode(Int.self, forKey: .congress)
    type = try container.decode(String.self, forKey: .type)
    number = try container.decode(String.self, forKey: .number)
    title = try container.decode(String.self, forKey: .title)
    shortTitle = try container.decodeIfPresent(String.self, forKey: .shortTitle)
    sponsor = try container.decode(Sponsor.self, forKey: .sponsor)
    introducedDate = try container.decode(String.self, forKey: .introducedDate)
    latestAction = try container.decode(LegislativeAction.self, forKey: .latestAction)
    outcome = try container.decodeIfPresent(BillOutcome.self, forKey: .outcome) ?? .unknown
    lifecycleStatus = try container.decodeIfPresent(LifecycleStatus.self, forKey: .lifecycleStatus)
    policyArea = try container.decodeIfPresent(String.self, forKey: .policyArea)
    subjects = try container.decodeIfPresent([String].self, forKey: .subjects) ?? []
    summaryCRS = try container.decodeIfPresent(String.self, forKey: .summaryCRS)
    textURLHTML = try container.decodeIfPresent(URL.self, forKey: .textURLHTML)
    congressURL = try container.decode(URL.self, forKey: .congressURL)
    votes = try container.decodeIfPresent([RollCallVoteReference].self, forKey: .votes) ?? []
  }
}

public struct Sponsor: Codable, Hashable, Sendable {
  public let name: String
  public let party: String
  public let state: String

  public init(name: String, party: String, state: String) {
    self.name = name
    self.party = party
    self.state = state
  }
}

public struct LegislativeAction: Codable, Hashable, Sendable {
  public let date: String
  public let text: String

  public init(date: String, text: String) {
    self.date = date
    self.text = text
  }
}

public enum BillOutcome: String, Codable, CaseIterable, Sendable {
  case passedHouse = "passed_house"
  case passedSenate = "passed_senate"
  case enacted
  case vetoed
  case failed
  case unknown

  public init(from decoder: any Decoder) throws {
    let value = try decoder.singleValueContainer().decode(String.self)
    self = Self(rawValue: value) ?? .unknown
  }
}

public enum LifecycleStatus: String, Codable, CaseIterable, Sendable {
  case introduced
  case inCommittee = "in_committee"
  case reported
  case unknown

  public init(from decoder: any Decoder) throws {
    let value = try decoder.singleValueContainer().decode(String.self)
    self = Self(rawValue: value) ?? .unknown
  }
}
