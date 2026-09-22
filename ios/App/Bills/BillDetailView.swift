import ICDesign
import ICFeatures
import ICModels
import SwiftUI

struct BillDetailView: View {
  let bill: Bill
  let votesCoverage: Bool

  var body: some View {
    List {
      Section {
        Text(bill.title)
          .font(.title3.weight(.semibold))
        LabeledContent("Sponsor", value: bill.sponsor.name)
        LabeledContent("Introduced", value: bill.introducedDate)
        if let policyArea = bill.policyArea {
          LabeledContent("Policy area", value: policyArea)
        }
      }

      Section("Latest action") {
        Text(bill.latestAction.text)
        Text(bill.latestAction.date)
          .foregroundStyle(.secondary)
      }

      if let summary = bill.summaryCRS {
        Section("Congressional Research Service summary") {
          Text(summary)
        }
      }

      if votesCoverage {
        Section("Votes") {
          if bill.votes.isEmpty {
            Text("No recorded roll call — passed by voice vote or unanimous consent.")
              .foregroundStyle(.secondary)
          } else {
            ForEach(bill.votes.reversed()) { vote in
              RollCallVoteCard(vote: vote)
            }
          }
        }
      }

      Section {
        Link(destination: bill.congressURL) {
          Label("View on Congress.gov", systemImage: "arrow.up.right.square")
        }
      }
    }
    .navigationTitle(bill.displayNumber)
    .navigationBarTitleDisplayMode(.inline)
    .toolbar(.hidden, for: .tabBar)
    .toolbar {
      ToolbarItem(placement: .topBarTrailing) {
        ShareLink(item: shareText) {
          Label("Share", systemImage: "square.and.arrow.up")
        }
      }
    }
    .accessibilityIdentifier("bill-detail")
  }

  private var shareText: String {
    "\(bill.displayNumber) — \(bill.title)\n\n\(bill.congressURL.absoluteString)"
  }
}

private struct RollCallVoteCard: View {
  let vote: RollCallVoteReference

  private var presentation: RollCallVotePresentation {
    RollCallVotePresentation(vote: vote)
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack(alignment: .firstTextBaseline) {
        Text("\(presentation.chamberName) · \(vote.date)")
          .font(.subheadline)
          .foregroundStyle(.secondary)
        Spacer()
        Text(vote.result)
          .font(.caption.weight(.semibold))
          .foregroundStyle(presentation.resultIndicatesPassage ? ICDesign.accent : .secondary)
          .padding(.horizontal, 10)
          .padding(.vertical, 4)
          .background(
            presentation.resultIndicatesPassage
              ? ICDesign.accent.opacity(0.12)
              : Color.secondary.opacity(0.12),
            in: Capsule()
          )
      }

      Text(vote.question)
      Text("Yea \(vote.totals.yea) · Nay \(vote.totals.nay)")
        .font(.headline)

      if let line = presentation.partySplitLine(for: "yea", label: "Yea") {
        detailLine(line)
      }
      if let line = presentation.partySplitLine(for: "nay", label: "Nay") {
        detailLine(line)
      }
      if vote.totals.present > 0 {
        detailLine("Present \(vote.totals.present)")
      }
      if vote.totals.notVoting > 0 {
        detailLine("Not voting \(vote.totals.notVoting)")
      }
    }
    .padding(.vertical, 4)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(presentation.accessibilityDescription)
    .accessibilityIdentifier("roll-call-\(vote.id)")
  }

  private func detailLine(_ text: String) -> some View {
    Text(text)
      .font(.subheadline)
      .foregroundStyle(.secondary)
  }
}
