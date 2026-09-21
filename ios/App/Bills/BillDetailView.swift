import ICDesign
import ICModels
import SwiftUI

struct BillDetailView: View {
  let bill: Bill

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
