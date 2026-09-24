import Foundation
import ICFeatures
import ICModels
import Testing

@Suite("Member presentation")
struct MemberPresentationTests {
  @Test("Formats district and native contact destinations")
  func contactDestinations() throws {
    let presentation = MemberPresentation(member: .sampleHouse)

    #expect(presentation.roleName == "Representative")
    #expect(presentation.partyAndJurisdiction == "R-AZ-1")
    #expect(presentation.phoneURL?.absoluteString == "tel:2022252190")
    #expect(presentation.primaryWebURL?.absoluteString == "https://schweikert.house.gov")
    #expect(presentation.socialLinks.map(\.label) == ["X (Twitter)"])
    #expect(presentation.socialLinks.first?.url.absoluteString == "https://x.com/RepDavid")
  }

  @Test("Prefers a contact form and ignores unknown social platforms")
  func contactPriority() {
    let member = Member(
      bioguideID: "T000001",
      name: "Taylor Example",
      party: "I",
      state: "AZ",
      chamber: .senate,
      officialURL: URL(string: "https://congress.example/member")!,
      contactForm: URL(string: "https://senate.example/contact")!,
      website: URL(string: "https://senate.example")!,
      socials: [SocialHandle(platform: "future-network", handle: "taylor")]
    )
    let presentation = MemberPresentation(member: member)

    #expect(presentation.partyAndJurisdiction == "I-AZ")
    #expect(presentation.primaryWebURL?.absoluteString == "https://senate.example/contact")
    #expect(presentation.socialLinks.isEmpty)
  }

  @Test("Matches the Android social URL contract")
  func socialURLs() {
    let member = Member(
      bioguideID: "T000002",
      name: "Social Example",
      party: "I",
      state: "AZ",
      chamber: .senate,
      socials: [
        SocialHandle(platform: "youtube", handle: "@channel"),
        SocialHandle(platform: "threads", handle: "@person"),
        SocialHandle(platform: "bluesky", handle: "person.example"),
      ]
    )

    let links = MemberPresentation(member: member).socialLinks

    #expect(links.map(\.label) == ["YouTube", "Threads", "Bluesky"])
    #expect(
      links.map(\.url.absoluteString) == [
        "https://www.youtube.com/@channel",
        "https://www.threads.net/@person",
        "https://bsky.app/profile/person.example",
      ])
  }
}
