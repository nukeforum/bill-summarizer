import ICDesign
import ICFeatures
import ICModels
import SwiftUI

struct BillsView: View {
  @Bindable var model: BillsFeatureModel

  var body: some View {
    NavigationStack {
      content
        .navigationTitle("Informed Citizen")
        .searchable(text: $model.searchQuery, prompt: "Search bills")
        .toolbar {
          ToolbarItem(placement: .topBarTrailing) {
            Button("Settings", systemImage: "gearshape") {}
              .accessibilityIdentifier("settings-button")
          }
        }
        .navigationDestination(for: Bill.self) { bill in
          BillDetailView(bill: bill)
        }
    }
    .task {
      guard case .loading = model.state else { return }
      await model.load()
    }
  }

  @ViewBuilder
  private var content: some View {
    switch model.state {
    case .loading:
      ProgressView("Loading bills…")
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("bills-loading")

    case .failed(let message):
      ContentUnavailableView {
        Label("Couldn’t Load Bills", systemImage: "wifi.exclamationmark")
      } description: {
        Text(message)
      } actions: {
        Button("Try Again") {
          Task { await model.load() }
        }
      }
      .accessibilityIdentifier("bills-error")

    case .loaded(let snapshot):
      if model.visibleBills.isEmpty {
        ContentUnavailableView.search(text: model.searchQuery)
      } else {
        List {
          Section {
            ForEach(model.visibleBills) { bill in
              NavigationLink(value: bill) {
                BillRow(bill: bill)
              }
            }
          } header: {
            Text("\(model.visibleBills.count) bills · Congress \(snapshot.congress)")
          } footer: {
            Text("Published \(snapshot.generatedAt)")
          }
        }
        .refreshable { await model.load() }
        .accessibilityIdentifier("bills-list")
      }
    }
  }
}

private struct BillRow: View {
  let bill: Bill

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack(alignment: .firstTextBaseline) {
        Text(bill.displayNumber)
          .font(.headline)
        Spacer()
        Text(bill.outcome.displayName)
          .font(.caption.weight(.semibold))
          .foregroundStyle(ICDesign.accent)
      }
      Text(bill.shortTitle ?? bill.title)
        .font(.body)
        .lineLimit(3)
      Text("\(bill.sponsor.name) · \(bill.sponsor.party)-\(bill.sponsor.state)")
        .font(.subheadline)
        .foregroundStyle(.secondary)
    }
    .padding(.vertical, 4)
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("bill-row-\(bill.id)")
  }
}

extension BillOutcome {
  fileprivate var displayName: String {
    switch self {
    case .passedHouse: "Passed House"
    case .passedSenate: "Passed Senate"
    case .enacted: "Enacted"
    case .vetoed: "Vetoed"
    case .failed: "Failed"
    case .unknown: "In progress"
    }
  }
}
