import Foundation

public struct MembersIndex: Codable, Equatable, Sendable {
  public let congress: Int
  public let generatedAt: String
  public let members: [Member]

  public init(congress: Int, generatedAt: String, members: [Member]) {
    self.congress = congress
    self.generatedAt = generatedAt
    self.members = members
  }

  private enum CodingKeys: String, CodingKey {
    case congress, members
    case generatedAt = "generated_at"
  }
}

public struct Member: Codable, Equatable, Hashable, Identifiable, Sendable {
  public let bioguideID: String
  public let name: String
  public let party: String
  public let state: String
  public let district: Int?
  public let chamber: MemberChamber
  public let photoURL: URL?
  public let officialURL: URL?
  public let sponsoredCount: Int
  public let cosponsoredCount: Int
  public let address: String?
  public let phone: String?
  public let contactForm: URL?
  public let website: URL?
  public let socials: [SocialHandle]
  public let nextElectionYear: Int?

  public var id: String { bioguideID }

  public init(
    bioguideID: String,
    name: String,
    party: String,
    state: String,
    district: Int? = nil,
    chamber: MemberChamber,
    photoURL: URL? = nil,
    officialURL: URL? = nil,
    sponsoredCount: Int = 0,
    cosponsoredCount: Int = 0,
    address: String? = nil,
    phone: String? = nil,
    contactForm: URL? = nil,
    website: URL? = nil,
    socials: [SocialHandle] = [],
    nextElectionYear: Int? = nil
  ) {
    self.bioguideID = bioguideID
    self.name = name
    self.party = party
    self.state = state
    self.district = district
    self.chamber = chamber
    self.photoURL = photoURL
    self.officialURL = officialURL
    self.sponsoredCount = sponsoredCount
    self.cosponsoredCount = cosponsoredCount
    self.address = address
    self.phone = phone
    self.contactForm = contactForm
    self.website = website
    self.socials = socials
    self.nextElectionYear = nextElectionYear
  }

  private enum CodingKeys: String, CodingKey {
    case name, party, state, district, chamber, address, phone, website, socials
    case bioguideID = "bioguide_id"
    case photoURL = "photo_url"
    case officialURL = "official_url"
    case sponsoredCount = "sponsored_count"
    case cosponsoredCount = "cosponsored_count"
    case contactForm = "contact_form"
    case nextElectionYear = "next_election_year"
  }

  public init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    bioguideID = try container.decode(String.self, forKey: .bioguideID)
    name = try container.decode(String.self, forKey: .name)
    party = try container.decode(String.self, forKey: .party)
    state = try container.decode(String.self, forKey: .state)
    district = try container.decodeIfPresent(Int.self, forKey: .district)
    chamber = try container.decode(MemberChamber.self, forKey: .chamber)
    photoURL = try container.decodeIfPresent(URL.self, forKey: .photoURL)
    officialURL = try container.decodeIfPresent(URL.self, forKey: .officialURL)
    sponsoredCount = try container.decodeIfPresent(Int.self, forKey: .sponsoredCount) ?? 0
    cosponsoredCount = try container.decodeIfPresent(Int.self, forKey: .cosponsoredCount) ?? 0
    address = try container.decodeIfPresent(String.self, forKey: .address)
    phone = try container.decodeIfPresent(String.self, forKey: .phone)
    contactForm = try container.decodeIfPresent(URL.self, forKey: .contactForm)
    website = try container.decodeIfPresent(URL.self, forKey: .website)
    socials = try container.decodeIfPresent([SocialHandle].self, forKey: .socials) ?? []
    nextElectionYear = try container.decodeIfPresent(Int.self, forKey: .nextElectionYear)
  }
}

public enum MemberChamber: String, Codable, Hashable, Sendable {
  case house
  case senate
  case unknown

  public init(from decoder: any Decoder) throws {
    let value = try decoder.singleValueContainer().decode(String.self)
    self = Self(rawValue: value) ?? .unknown
  }
}

public struct SocialHandle: Codable, Equatable, Hashable, Sendable {
  public let platform: String
  public let handle: String

  public init(platform: String, handle: String) {
    self.platform = platform
    self.handle = handle
  }
}

public struct SavedRepresentativesSelection: Codable, Equatable, Sendable {
  public let stateCode: String
  public let district: Int
  public let bioguideIDs: Set<String>

  public init(stateCode: String, district: Int, bioguideIDs: Set<String>) {
    self.stateCode = stateCode
    self.district = district
    self.bioguideIDs = bioguideIDs
  }
}
