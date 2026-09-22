import ICModels

public struct RollCallVotePresentation: Equatable, Sendable {
  public let vote: RollCallVoteReference

  public init(vote: RollCallVoteReference) {
    self.vote = vote
  }

  public var chamberName: String {
    switch vote.chamber {
    case .house: "House"
    case .senate: "Senate"
    case .unknown: "Congress"
    }
  }

  public var resultIndicatesPassage: Bool {
    let normalized = vote.result.localizedLowercase
    return normalized.contains("passed") || normalized.contains("agreed to")
  }

  public func partySplitLine(for position: String, label: String) -> String? {
    guard let counts = vote.partySplit[position], !counts.isEmpty else { return nil }
    return "\(label) — "
      + orderedParties(in: counts)
      .map { "\($0.key) \($0.value)" }
      .joined(separator: " · ")
  }

  public var accessibilityDescription: String {
    var components = [
      "\(chamberName), \(vote.date), \(vote.question), \(vote.result).",
      "\(vote.totals.yea) yea, \(vote.totals.nay) nay.",
    ]
    if let line = accessiblePartySplit(for: "yea", label: "Yea") {
      components.append(line)
    }
    if let line = accessiblePartySplit(for: "nay", label: "Nay") {
      components.append(line)
    }
    if vote.totals.present > 0 {
      components.append("\(vote.totals.present) present.")
    }
    if vote.totals.notVoting > 0 {
      components.append("\(vote.totals.notVoting) not voting.")
    }
    return components.joined(separator: " ")
  }

  private func accessiblePartySplit(for position: String, label: String) -> String? {
    guard let counts = vote.partySplit[position], !counts.isEmpty else { return nil }
    let values = orderedParties(in: counts).map { entry in
      "\(entry.value) \(partyName(code: entry.key, count: entry.value))"
    }
    return "\(label): \(values.joined(separator: ", "))."
  }

  private func orderedParties(in counts: [String: Int]) -> [(key: String, value: Int)] {
    let preferredOrder = ["D", "R", "I"]
    return counts.sorted { lhs, rhs in
      let lhsIndex = preferredOrder.firstIndex(of: lhs.key) ?? preferredOrder.count
      let rhsIndex = preferredOrder.firstIndex(of: rhs.key) ?? preferredOrder.count
      return lhsIndex == rhsIndex ? lhs.key < rhs.key : lhsIndex < rhsIndex
    }
  }

  private func partyName(code: String, count: Int) -> String {
    switch code {
    case "D": count == 1 ? "Democrat" : "Democrats"
    case "R": count == 1 ? "Republican" : "Republicans"
    case "I": count == 1 ? "Independent" : "Independents"
    default: code
    }
  }
}
