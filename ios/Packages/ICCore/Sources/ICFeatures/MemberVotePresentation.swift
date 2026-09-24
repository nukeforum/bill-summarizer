import Foundation
import ICModels

public struct MemberVotePresentation: Equatable, Sendable {
  public let vote: MemberVoteRow

  public init(vote: MemberVoteRow) {
    self.vote = vote
  }

  public var positionLabel: String {
    switch vote.position {
    case .yea: "Yea"
    case .nay: "Nay"
    case .present: "Present"
    case .notVoting: "Not voting"
    case .unknown: "Unknown"
    }
  }

  public var formattedDate: String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    guard let date = formatter.date(from: vote.date) else { return vote.date }
    formatter.dateFormat = "MMM d, yyyy"
    return formatter.string(from: date)
  }

  public var accessibilityDescription: String {
    var components = [vote.billLabel]
    if let shortTitle = vote.shortTitle, !shortTitle.isEmpty {
      components.append(shortTitle)
    }
    components.append(formattedDate)
    components.append(vote.question)
    components.append("Result: \(vote.result)")
    components.append("Voted \(positionLabel.lowercased())")
    return components.joined(separator: ". ") + "."
  }
}
