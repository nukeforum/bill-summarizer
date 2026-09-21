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
    congressURL: URL(string: "https://www.congress.gov/bill/119th-congress/house-bill/1")!
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
