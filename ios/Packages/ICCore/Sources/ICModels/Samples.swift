import Foundation

extension Bill {
  public static let sample = Bill(
    id: "hr-1-119",
    congress: 119,
    type: "hr",
    number: "1",
    title: "A bill to improve public access to legislative information.",
    shortTitle: "Public Information Act",
    sponsor: Sponsor(name: "Jordan Lee", party: "I", state: "AZ"),
    introducedDate: "2026-01-08",
    latestAction: LegislativeAction(
      date: "2026-09-17",
      text: "Passed House by recorded vote"
    ),
    outcome: .passedHouse,
    policyArea: "Government Operations and Politics",
    subjects: ["Government information and archives"],
    summaryCRS: "Makes legislative information easier for the public to find and understand.",
    congressURL: URL(string: "https://www.congress.gov/bill/119th-congress/house-bill/1")!,
    votes: [
      RollCallVoteReference(
        id: "house-119-1-17",
        chamber: .house,
        session: 1,
        rollNumber: 17,
        date: "2026-09-17",
        question: "On Passage",
        result: "Passed",
        billID: "hr-1-119",
        totals: VoteTotals(yea: 218, nay: 210, present: 0, notVoting: 7),
        partySplit: [
          "yea": ["D": 210, "R": 8],
          "nay": ["R": 210],
          "not_voting": ["D": 2, "R": 5],
        ],
        path: "votes/congress119/house-1-17.json"
      )
    ]
  )

  public static let samples: [Bill] = [
    .sample,
    Bill(
      id: "s-42-119",
      congress: 119,
      type: "s",
      number: "42",
      title: "A bill concerning conservation and public lands.",
      sponsor: Sponsor(name: "Alex Morgan", party: "D", state: "CO"),
      introducedDate: "2026-02-12",
      latestAction: LegislativeAction(
        date: "2026-09-15",
        text: "Read twice and referred to committee"
      ),
      outcome: .unknown,
      lifecycleStatus: .inCommittee,
      policyArea: "Public Lands and Natural Resources",
      congressURL: URL(string: "https://www.congress.gov/bill/119th-congress/senate-bill/42")!
    ),
  ]
}

extension Member {
  public static let sampleHouse = Member(
    bioguideID: "S001183",
    name: "David Schweikert",
    party: "R",
    state: "AZ",
    district: 1,
    chamber: .house,
    phone: "(202) 225-2190",
    website: URL(string: "https://schweikert.house.gov"),
    socials: [SocialHandle(platform: "twitter", handle: "RepDavid")]
  )

  public static let sampleSenators = [
    Member(
      bioguideID: "K000377",
      name: "Mark Kelly",
      party: "D",
      state: "AZ",
      chamber: .senate,
      phone: "(202) 224-2235",
      contactForm: URL(string: "https://www.kelly.senate.gov/contact/contact-form/"),
      website: URL(string: "https://www.kelly.senate.gov"),
      socials: [SocialHandle(platform: "instagram", handle: "senmarkkelly")]
    ),
    Member(
      bioguideID: "G000574",
      name: "Ruben Gallego",
      party: "D",
      state: "AZ",
      chamber: .senate,
      phone: "(202) 224-4521",
      website: URL(string: "https://www.gallego.senate.gov")
    ),
  ]
}

extension MembersIndex {
  public static let sample = MembersIndex(
    congress: 119,
    generatedAt: "2026-09-20T08:12:34Z",
    members: Member.sampleSenators + [Member.sampleHouse]
  )
}

extension MemberLegislation {
  public static let sampleSponsored = MemberLegislation(
    bioguideID: "K000377",
    congress: 119,
    kind: "sponsored",
    generatedAt: "2026-09-20T08:12:34Z",
    bills: [
      MemberLegislationItem(
        id: "s4478-119",
        type: "s",
        number: "4478",
        congress: 119,
        title: "Federal Worker Credit Protection Act",
        introducedDate: "2026-04-30",
        latestAction: LegislativeAction(
          date: "2026-04-30",
          text: "Referred to the Committee on Banking, Housing, and Urban Affairs."
        ),
        policyArea: "Government Operations and Politics"
      ),
      MemberLegislationItem(
        id: "s2448-119",
        type: "s",
        number: "2448",
        congress: 119,
        title: "Health Care Fairness for Military Families Act of 2025",
        introducedDate: "2025-07-24",
        latestAction: LegislativeAction(
          date: "2025-07-24",
          text: "Referred to the Committee on Armed Services."
        ),
        policyArea: "Armed Forces and National Security"
      ),
    ]
  )

  public static let sampleCosponsored = MemberLegislation(
    bioguideID: "K000377",
    congress: 119,
    kind: "cosponsored",
    generatedAt: "2026-09-20T08:12:34Z",
    bills: [
      MemberLegislationItem(
        id: "hr1-119",
        type: "hr",
        number: "1",
        congress: 119,
        title: "Public Information Act",
        introducedDate: "2026-01-08",
        latestAction: LegislativeAction(date: "2026-09-17", text: "Passed House."),
        policyArea: "Government Operations and Politics"
      )
    ]
  )
}

extension MemberVotes {
  public static let sample = MemberVotes(
    generatedAt: "2026-09-23T11:58:00Z",
    bioguideID: "K000377",
    voteCount: 3,
    votes: [
      MemberVoteRow(
        voteID: "senate-119-2-240",
        congress: 119,
        date: "2026-09-22",
        question: "On the Cloture Motion",
        result: "Cloture Motion Agreed to",
        position: .nay
      ),
      MemberVoteRow(
        voteID: "senate-119-2-236",
        congress: 119,
        date: "2026-09-17",
        question: "On the Motion to Proceed",
        result: "Motion to Proceed Agreed to",
        position: .nay,
        billID: "s4668-119",
        type: "s",
        number: "4668",
        shortTitle: "Affordable Housing Act"
      ),
      MemberVoteRow(
        voteID: "senate-119-2-228",
        congress: 119,
        date: "2026-08-08",
        question: "On Passage of the Bill",
        result: "Bill Passed",
        position: .yea,
        billID: "hr6500-119",
        type: "hr",
        number: "6500",
        shortTitle: "Community Preparedness Act"
      ),
    ]
  )
}
