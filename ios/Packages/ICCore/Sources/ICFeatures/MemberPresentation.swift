import Foundation
import ICModels

public struct MemberPresentation: Equatable, Sendable {
  public struct SocialLink: Equatable, Sendable {
    public let label: String
    public let url: URL

    public init(label: String, url: URL) {
      self.label = label
      self.url = url
    }
  }

  public let member: Member

  public init(member: Member) {
    self.member = member
  }

  public var roleName: String {
    switch member.chamber {
    case .house: "Representative"
    case .senate: "Senator"
    case .unknown: "Member of Congress"
    }
  }

  public var partyAndJurisdiction: String {
    if member.chamber == .house, let district = member.district, district > 0 {
      return "\(member.party)-\(member.state)-\(district)"
    }
    return "\(member.party)-\(member.state)"
  }

  public var phoneURL: URL? {
    guard let phone = member.phone else { return nil }
    let digits = phone.filter(\.isNumber)
    guard !digits.isEmpty else { return nil }
    return URL(string: "tel:\(digits)")
  }

  public var primaryWebURL: URL? {
    member.contactForm ?? member.website ?? member.officialURL
  }

  public var socialLinks: [SocialLink] {
    member.socials.compactMap { social in
      let destination: (String, String)? =
        switch social.platform.lowercased() {
        case "twitter", "x": ("X (Twitter)", "https://x.com/\(handleWithoutAt(social.handle))")
        case "facebook": ("Facebook", "https://www.facebook.com/\(handleWithoutAt(social.handle))")
        case "instagram":
          (
            "Instagram", "https://www.instagram.com/\(handleWithoutAt(social.handle))"
          )
        case "youtube": ("YouTube", "https://www.youtube.com/\(social.handle)")
        case "threads": ("Threads", "https://www.threads.net/@\(handleWithoutAt(social.handle))")
        case "bluesky": ("Bluesky", "https://bsky.app/profile/\(handleWithoutAt(social.handle))")
        default: nil
        }
      guard let destination, let url = URL(string: destination.1) else { return nil }
      return SocialLink(label: destination.0, url: url)
    }
  }

  private func handleWithoutAt(_ handle: String) -> String {
    handle.trimmingCharacters(in: CharacterSet(charactersIn: "@"))
  }
}
