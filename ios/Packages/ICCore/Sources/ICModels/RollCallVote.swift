public enum VoteChamber: String, Codable, Hashable, Sendable {
  case house
  case senate
  case unknown

  public init(from decoder: any Decoder) throws {
    let value = try decoder.singleValueContainer().decode(String.self)
    self = Self(rawValue: value) ?? .unknown
  }
}

public struct VoteTotals: Codable, Equatable, Hashable, Sendable {
  public let yea: Int
  public let nay: Int
  public let present: Int
  public let notVoting: Int

  public init(yea: Int, nay: Int, present: Int, notVoting: Int) {
    self.yea = yea
    self.nay = nay
    self.present = present
    self.notVoting = notVoting
  }

  private enum CodingKeys: String, CodingKey {
    case yea, nay, present
    case notVoting = "not_voting"
  }
}

public struct RollCallVoteReference: Codable, Equatable, Hashable, Identifiable, Sendable {
  public let id: String
  public let chamber: VoteChamber
  public let session: Int
  public let rollNumber: Int
  public let date: String
  public let question: String
  public let result: String
  public let billID: String?
  public let totals: VoteTotals
  public let partySplit: [String: [String: Int]]
  public let path: String

  public init(
    id: String,
    chamber: VoteChamber,
    session: Int,
    rollNumber: Int,
    date: String,
    question: String,
    result: String,
    billID: String? = nil,
    totals: VoteTotals,
    partySplit: [String: [String: Int]] = [:],
    path: String
  ) {
    self.id = id
    self.chamber = chamber
    self.session = session
    self.rollNumber = rollNumber
    self.date = date
    self.question = question
    self.result = result
    self.billID = billID
    self.totals = totals
    self.partySplit = partySplit
    self.path = path
  }

  private enum CodingKeys: String, CodingKey {
    case id, chamber, session, date, question, result, totals, path
    case rollNumber = "roll_number"
    case billID = "bill_id"
    case partySplit = "party_split"
  }

  public init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    id = try container.decode(String.self, forKey: .id)
    chamber = try container.decode(VoteChamber.self, forKey: .chamber)
    session = try container.decode(Int.self, forKey: .session)
    rollNumber = try container.decode(Int.self, forKey: .rollNumber)
    date = try container.decode(String.self, forKey: .date)
    question = try container.decode(String.self, forKey: .question)
    result = try container.decode(String.self, forKey: .result)
    billID = try container.decodeIfPresent(String.self, forKey: .billID)
    totals = try container.decode(VoteTotals.self, forKey: .totals)
    partySplit =
      try container.decodeIfPresent([String: [String: Int]].self, forKey: .partySplit) ?? [:]
    path = try container.decode(String.self, forKey: .path)
  }
}
