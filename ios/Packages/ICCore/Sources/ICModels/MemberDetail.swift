import Foundation

public struct MemberLegislation: Codable, Equatable, Sendable {
  public let bioguideID: String
  public let congress: Int
  public let kind: String
  public let generatedAt: String
  public let bills: [MemberLegislationItem]

  public init(
    bioguideID: String,
    congress: Int,
    kind: String,
    generatedAt: String,
    bills: [MemberLegislationItem]
  ) {
    self.bioguideID = bioguideID
    self.congress = congress
    self.kind = kind
    self.generatedAt = generatedAt
    self.bills = bills
  }

  public var renderableBills: [MemberLegislationItem] {
    bills.filter { !$0.type.isEmpty && !$0.number.isEmpty }
  }

  private enum CodingKeys: String, CodingKey {
    case congress, kind, bills
    case bioguideID = "bioguide_id"
    case generatedAt = "generated_at"
  }
}

public struct MemberLegislationItem: Codable, Equatable, Hashable, Identifiable, Sendable {
  public let id: String
  public let type: String
  public let number: String
  public let congress: Int
  public let title: String
  public let introducedDate: String
  public let latestAction: LegislativeAction
  public let policyArea: String?

  public init(
    id: String,
    type: String,
    number: String,
    congress: Int,
    title: String,
    introducedDate: String,
    latestAction: LegislativeAction,
    policyArea: String? = nil
  ) {
    self.id = id
    self.type = type
    self.number = number
    self.congress = congress
    self.title = title
    self.introducedDate = introducedDate
    self.latestAction = latestAction
    self.policyArea = policyArea
  }

  public var displayNumber: String {
    "\(Self.displayType(type)) \(number)"
  }

  public var congressURL: URL? {
    guard let route = Self.congressRoute(type) else { return nil }
    return URL(
      string: "https://www.congress.gov/bill/\(congress)th-congress/\(route)/\(number)")
  }

  private enum CodingKeys: String, CodingKey {
    case id, type, number, congress, title
    case introducedDate = "introduced_date"
    case latestAction = "latest_action"
    case policyArea = "policy_area"
  }

  private static func displayType(_ type: String) -> String {
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
  }

  private static func congressRoute(_ type: String) -> String? {
    switch type.lowercased() {
    case "hr": "house-bill"
    case "s": "senate-bill"
    case "hres": "house-resolution"
    case "sres": "senate-resolution"
    case "hjres": "house-joint-resolution"
    case "sjres": "senate-joint-resolution"
    case "hconres": "house-concurrent-resolution"
    case "sconres": "senate-concurrent-resolution"
    default: nil
    }
  }
}

public struct MemberVotes: Codable, Equatable, Sendable {
  public let generatedAt: String
  public let bioguideID: String
  public let voteCount: Int
  public let votes: [MemberVoteRow]

  public init(
    generatedAt: String,
    bioguideID: String,
    voteCount: Int,
    votes: [MemberVoteRow]
  ) {
    self.generatedAt = generatedAt
    self.bioguideID = bioguideID
    self.voteCount = voteCount
    self.votes = votes
  }

  private enum CodingKeys: String, CodingKey {
    case votes
    case generatedAt = "generated_at"
    case bioguideID = "bioguide_id"
    case voteCount = "vote_count"
  }
}

public struct MemberVoteRow: Codable, Equatable, Hashable, Identifiable, Sendable {
  public let voteID: String
  public let congress: Int
  public let date: String
  public let question: String
  public let result: String
  public let position: VotePosition
  public let billID: String?
  public let type: String?
  public let number: String?
  public let shortTitle: String?

  public var id: String { voteID }

  public init(
    voteID: String,
    congress: Int,
    date: String,
    question: String,
    result: String,
    position: VotePosition,
    billID: String? = nil,
    type: String? = nil,
    number: String? = nil,
    shortTitle: String? = nil
  ) {
    self.voteID = voteID
    self.congress = congress
    self.date = date
    self.question = question
    self.result = result
    self.position = position
    self.billID = billID
    self.type = type
    self.number = number
    self.shortTitle = shortTitle
  }

  public var billLabel: String {
    guard let type, !type.isEmpty, let number, !number.isEmpty else { return question }
    return "\(type.uppercased()) \(number)"
  }

  public var congressURL: URL? {
    guard
      let type,
      let number,
      !type.isEmpty,
      !number.isEmpty
    else { return nil }
    return MemberLegislationItem(
      id: billID ?? voteID,
      type: type,
      number: number,
      congress: congress,
      title: shortTitle ?? question,
      introducedDate: date,
      latestAction: LegislativeAction(date: date, text: result)
    ).congressURL
  }

  private enum CodingKeys: String, CodingKey {
    case congress, date, question, result, position, type, number
    case voteID = "vote_id"
    case billID = "bill_id"
    case shortTitle = "short_title"
  }
}

public enum VotePosition: String, Codable, Equatable, Hashable, Sendable {
  case yea
  case nay
  case present
  case notVoting = "not_voting"
  case unknown

  public init(from decoder: any Decoder) throws {
    let value = try decoder.singleValueContainer().decode(String.self)
    self = Self(rawValue: value) ?? .unknown
  }
}
