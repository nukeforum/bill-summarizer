import ICDesign
import ICFeatures
import ICModels
import SwiftUI

struct MemberDetailView: View {
  @Bindable var model: MemberDetailFeatureModel

  var body: some View {
    content
      .navigationTitle(model.member.name)
      .navigationBarTitleDisplayMode(.inline)
      .toolbar(.hidden, for: .tabBar)
      .task {
        guard case .loading = model.state else { return }
        await model.load()
      }
  }

  @ViewBuilder
  private var content: some View {
    switch model.state {
    case .loading:
      ProgressView("Loading record…")
        .frame(maxWidth: .infinity, maxHeight: .infinity)

    case .failed(let message):
      ContentUnavailableView {
        Label("Couldn’t Load Record", systemImage: "wifi.exclamationmark")
      } description: {
        Text(message)
      } actions: {
        Button("Try Again") { Task { await model.load() } }
      }

    case .loaded(let snapshot):
      MemberDetailList(model: model, snapshot: snapshot)
    }
  }
}

private struct MemberDetailList: View {
  @Bindable var model: MemberDetailFeatureModel
  let snapshot: MemberDetailSnapshot

  var body: some View {
    List {
      Section {
        MemberProfileHeader(member: snapshot.member)
      }

      Section {
        Picker("Record", selection: $model.selectedTab) {
          Text("Votes").tag(MemberDetailFeatureModel.Tab.votes)
          Text("Sponsored").tag(MemberDetailFeatureModel.Tab.sponsored)
          Text("Cosponsored").tag(MemberDetailFeatureModel.Tab.cosponsored)
        }
        .pickerStyle(.segmented)
        .accessibilityIdentifier("member-detail-tabs")
      }

      switch model.selectedTab {
      case .votes:
        votesSection
      case .sponsored, .cosponsored:
        legislationSection
      }
    }
    .accessibilityIdentifier("member-detail")
  }

  @ViewBuilder
  private var votesSection: some View {
    Section {
      if snapshot.recentVotes.isEmpty {
        Text("No recorded votes yet for this representative.")
          .foregroundStyle(.secondary)
      } else {
        ForEach(snapshot.recentVotes) { vote in
          MemberVoteRowView(vote: vote)
        }
      }
    } header: {
      Text("Recent voting record (\(snapshot.recentVotes.count))")
    } footer: {
      if !snapshot.recentVotes.isEmpty {
        Text("Showing up to the 25 most recent published roll calls.")
      }
    }
  }

  @ViewBuilder
  private var legislationSection: some View {
    Section {
      TextField("Search bills", text: $model.searchQuery)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .accessibilityIdentifier("member-legislation-search")
    }

    Section(legislationSectionTitle) {
      if model.visibleLegislation.isEmpty {
        Text(legislationEmptyMessage)
          .foregroundStyle(.secondary)
      } else {
        ForEach(model.visibleLegislation) { item in
          MemberLegislationRow(item: item)
        }
      }
    }
  }

  private var legislationSectionTitle: String {
    let noun = model.selectedTab == .sponsored ? "Sponsored" : "Cosponsored"
    return "\(noun) bills (\(model.visibleLegislation.count))"
  }

  private var legislationEmptyMessage: String {
    if model.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
      return "No data yet for this representative."
    }
    return "No bills match “\(model.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines))”."
  }
}

private struct MemberProfileHeader: View {
  let member: Member

  private var presentation: MemberPresentation {
    MemberPresentation(member: member)
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 10) {
      Text(member.name)
        .font(.title3.weight(.semibold))
      Text("\(presentation.roleName) · \(presentation.partyAndJurisdiction)")
        .foregroundStyle(.secondary)

      if let address = member.address, !address.isEmpty {
        Label(address, systemImage: "mappin.and.ellipse")
          .font(.subheadline)
          .foregroundStyle(.secondary)
      }

      HStack(spacing: 16) {
        if let phoneURL = presentation.phoneURL {
          Link(destination: phoneURL) {
            Label("Call", systemImage: "phone")
          }
        }
        if let webURL = presentation.primaryWebURL {
          Link(destination: webURL) {
            Label("Contact", systemImage: "safari")
          }
        }
        if !presentation.socialLinks.isEmpty {
          Menu("Social", systemImage: "at") {
            ForEach(presentation.socialLinks, id: \.url) { link in
              Link(link.label, destination: link.url)
            }
          }
        }
      }
      .font(.subheadline)
    }
    .padding(.vertical, 4)
  }
}

private struct MemberVoteRowView: View {
  let vote: MemberVoteRow

  private var presentation: MemberVotePresentation {
    MemberVotePresentation(vote: vote)
  }

  var body: some View {
    Group {
      if let url = vote.congressURL {
        Link(destination: url) { rowContent }
          .buttonStyle(.plain)
      } else {
        rowContent
      }
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(presentation.accessibilityDescription)
    .accessibilityIdentifier("member-vote-\(vote.voteID)")
  }

  private var rowContent: some View {
    VStack(alignment: .leading, spacing: 6) {
      HStack(alignment: .firstTextBaseline) {
        Text(vote.billLabel)
          .font(.headline)
        Spacer()
        Text(presentation.positionLabel)
          .font(.caption.weight(.semibold))
          .foregroundStyle(positionColor)
          .padding(.horizontal, 9)
          .padding(.vertical, 4)
          .background(positionColor.opacity(0.12), in: Capsule())
      }
      if let shortTitle = vote.shortTitle, !shortTitle.isEmpty {
        Text(shortTitle)
          .lineLimit(2)
      }
      Text("\(presentation.formattedDate) · \(vote.question)")
        .font(.subheadline)
        .foregroundStyle(.secondary)
        .lineLimit(2)
      Text(vote.result)
        .font(.caption)
        .foregroundStyle(.secondary)
    }
    .padding(.vertical, 4)
  }

  private var positionColor: Color {
    switch vote.position {
    case .yea: ICDesign.accent
    case .nay: .red
    case .present, .notVoting, .unknown: .secondary
    }
  }
}

private struct MemberLegislationRow: View {
  let item: MemberLegislationItem

  var body: some View {
    Group {
      if let url = item.congressURL {
        Link(destination: url) { rowContent }
          .buttonStyle(.plain)
      } else {
        rowContent
      }
    }
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("member-legislation-\(item.id)")
  }

  private var rowContent: some View {
    VStack(alignment: .leading, spacing: 6) {
      Text(item.displayNumber)
        .font(.headline)
      Text(item.title)
        .lineLimit(3)
      Text("Latest action \(item.latestAction.date): \(item.latestAction.text)")
        .font(.subheadline)
        .foregroundStyle(.secondary)
        .lineLimit(2)
    }
    .padding(.vertical, 4)
  }
}
