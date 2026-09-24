import ICFeatures
import ICModels
import SwiftUI

struct RepresentativesView: View {
  @Bindable var model: RepresentativesFeatureModel
  @State private var showsRemoveConfirmation = false

  var body: some View {
    NavigationStack {
      content
        .navigationTitle("Representatives")
        .toolbar {
          if case .loaded = model.state {
            ToolbarItem(placement: .topBarTrailing) {
              Menu("Representative options", systemImage: "ellipsis.circle") {
                Button("Change location", systemImage: "location") {
                  model.changeLocation()
                }
                Button("Remove saved representatives", systemImage: "trash", role: .destructive) {
                  showsRemoveConfirmation = true
                }
              }
            }
          }
        }
    }
    .task {
      guard case .loading = model.state else { return }
      await model.load()
    }
    .confirmationDialog(
      "Remove saved representatives?",
      isPresented: $showsRemoveConfirmation,
      titleVisibility: .visible
    ) {
      Button("Remove", role: .destructive) {
        Task { await model.removeSavedRepresentatives() }
      }
    } message: {
      Text("You can add them again by choosing your location.")
    }
  }

  @ViewBuilder
  private var content: some View {
    switch model.state {
    case .loading:
      ProgressView("Loading representatives…")
        .frame(maxWidth: .infinity, maxHeight: .infinity)

    case .choosing(let message):
      LocationPicker(model: model, message: message)

    case .loaded(let snapshot):
      RepresentativesList(snapshot: snapshot)

    case .staleSavedRepresentatives:
      ContentUnavailableView {
        Label(
          "Update Your Representatives", systemImage: "person.crop.circle.badge.exclamationmark")
      } description: {
        Text("Your saved representatives are not all present in the current Congress.")
      } actions: {
        Button("Choose Again") { model.changeLocation() }
      }

    case .failed(let message):
      ContentUnavailableView {
        Label("Couldn’t Load Representatives", systemImage: "wifi.exclamationmark")
      } description: {
        Text(message)
      } actions: {
        Button("Try Again") { Task { await model.load() } }
      }
    }
  }
}

private struct LocationPicker: View {
  let model: RepresentativesFeatureModel
  let message: String?

  var body: some View {
    Form {
      Section {
        Text(
          "Choose your state and congressional district to see your House representative and senators."
        )
      }

      Section("Location") {
        Picker(
          "State or territory",
          selection: Binding(
            get: { model.selectedState ?? "" },
            set: { if !$0.isEmpty { model.selectState($0) } }
          )
        ) {
          Text("Select").tag("")
          ForEach(model.availableStates, id: \.self) { state in
            Text(state).tag(state)
          }
        }

        if model.selectedState != nil {
          Picker(
            "District",
            selection: Binding(
              get: { model.selectedDistrict ?? -1 },
              set: { if $0 >= 0 { model.selectDistrict($0) } }
            )
          ) {
            Text("Select").tag(-1)
            ForEach(model.availableDistricts, id: \.self) { district in
              Text(district == 0 ? "At large" : "District \(district)").tag(district)
            }
          }
          .disabled(model.availableDistricts.count == 1)
        }
      }

      if let message {
        Section {
          Label(message, systemImage: "exclamationmark.triangle")
            .foregroundStyle(.red)
        }
      }

      Section {
        Button("Save Representatives") {
          Task { await model.saveSelection() }
        }
        .disabled(!model.canSaveSelection)
        .frame(maxWidth: .infinity)
      }
    }
    .accessibilityIdentifier("representatives-location-picker")
  }
}

private struct RepresentativesList: View {
  let snapshot: RepresentativesSnapshot

  var body: some View {
    List {
      Section {
        Text(locationLabel)
          .font(.subheadline)
          .foregroundStyle(.secondary)
      }

      Section("Senators") {
        if snapshot.senators.isEmpty {
          Text("Your senators’ data can’t be located.")
            .foregroundStyle(.secondary)
        } else {
          ForEach(snapshot.senators) { member in
            RepresentativeRow(member: member)
          }
        }
      }

      Section("House Representative") {
        if snapshot.house.isEmpty {
          Text("Your House representative’s data can’t be located.")
            .foregroundStyle(.secondary)
        } else {
          ForEach(snapshot.house) { member in
            RepresentativeRow(member: member)
          }
        }
      }
    }
    .accessibilityIdentifier("representatives-list")
  }

  private var locationLabel: String {
    snapshot.selection.district == 0
      ? snapshot.selection.stateCode
      : "\(snapshot.selection.stateCode)-\(snapshot.selection.district)"
  }
}

private struct RepresentativeRow: View {
  let member: Member

  private var presentation: MemberPresentation {
    MemberPresentation(member: member)
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 10) {
      HStack(alignment: .firstTextBaseline) {
        VStack(alignment: .leading, spacing: 3) {
          Text(member.name)
            .font(.headline)
          Text("\(presentation.roleName) · \(presentation.partyAndJurisdiction)")
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
        Spacer()
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
    .accessibilityIdentifier("representative-\(member.bioguideID)")
  }
}
