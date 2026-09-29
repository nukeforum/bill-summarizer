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
      RepresentativesList(model: model, snapshot: snapshot)

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
  @Bindable var model: RepresentativesFeatureModel
  let message: String?

  private let houseLookupURL = URL(
    string: "https://www.house.gov/representatives/find-your-representative"
  )!

  var body: some View {
    Form {
      Section {
        Text(
          "Enter your ZIP code or choose a district to see your House representative and senators."
        )
      }

      Section {
        Picker(
          "Location method",
          selection: Binding(
            get: { model.locationSelectionMode },
            set: { mode in model.setLocationSelectionMode(mode) }
          )
        ) {
          Text("ZIP code").tag(LocationSelectionMode.zipCode)
          Text("Pick district").tag(LocationSelectionMode.district)
        }
        .pickerStyle(.segmented)
        .accessibilityIdentifier("representative-location-method")
      }

      switch model.locationSelectionMode {
      case .zipCode:
        Section("ZIP code") {
          HStack {
            TextField(
              "5-digit ZIP code",
              text: Binding(
                get: { model.zipCode },
                set: { value in model.setZipCode(value) }
              )
            )
            .keyboardType(.numberPad)
            .textContentType(.postalCode)
            .accessibilityIdentifier("representative-zip-code")

            Button {
              Task { await model.lookUpZip() }
            } label: {
              if model.zipLookupStatus == .lookingUp {
                ProgressView()
              } else {
                Label("Look Up", systemImage: "magnifyingglass")
                  .labelStyle(.iconOnly)
              }
            }
            .disabled(!model.canLookUpZip)
            .accessibilityLabel("Look up ZIP code")
            .accessibilityIdentifier("representative-zip-lookup")
          }

          if let zipMessage {
            Label(zipMessage.text, systemImage: zipMessage.symbol)
              .foregroundStyle(zipMessage.isError ? .red : .secondary)
          }
        }

        Section {
          Link(destination: houseLookupURL) {
            Label("Look up on House.gov", systemImage: "safari")
          }
        } footer: {
          Text("ZIP codes can span more than one congressional district.")
        }

      case .district:
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
            .accessibilityIdentifier("representative-district")
          }
        }

        if case .multiple(let stateCode, let districts) = model.zipLookupStatus {
          Section {
            Label(
              "This ZIP spans \(stateCode) districts \(districtList(districts)). Choose one to continue.",
              systemImage: "map"
            )
          }
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
    .alert(
      zipConfirmationTitle,
      isPresented: Binding(
        get: { model.showsZipConfirmation },
        set: { if !$0 { model.dismissZipConfirmation() } }
      )
    ) {
      Button("Cancel", role: .cancel) { model.dismissZipConfirmation() }
      Button("Save Representatives") {
        Task { await model.confirmZipSelection() }
      }
    } message: {
      Text("We matched that ZIP to this district. Save its representatives?")
    }
  }

  private var zipConfirmationTitle: String {
    guard let state = model.selectedState, let district = model.selectedDistrict else {
      return "Use this district?"
    }
    return district == 0 ? "Use \(state)?" : "Use \(state)-\(district)?"
  }

  private func districtList(_ districts: [Int]) -> String {
    districts.map { "\($0)" }.joined(separator: ", ")
  }

  private var zipMessage: (text: String, symbol: String, isError: Bool)? {
    switch model.zipLookupStatus {
    case .invalid:
      ("Enter a 5-digit ZIP code.", "exclamationmark.triangle", true)
    case .notFound:
      ("We couldn’t match that ZIP. Try another or pick your district.", "mappin.slash", true)
    case .failed:
      ("ZIP lookup is unavailable right now. Please try again.", "wifi.exclamationmark", true)
    case .matched(let stateCode, let district):
      (
        "Matched \(district == 0 ? stateCode : "\(stateCode)-\(district)").",
        "checkmark.circle",
        false
      )
    case .idle, .lookingUp, .multiple:
      nil
    }
  }
}

private struct RepresentativesList: View {
  let model: RepresentativesFeatureModel
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
            RepresentativeRow(model: model, member: member)
          }
        }
      }

      Section("House Representative") {
        if snapshot.house.isEmpty {
          Text("Your House representative’s data can’t be located.")
            .foregroundStyle(.secondary)
        } else {
          ForEach(snapshot.house) { member in
            RepresentativeRow(model: model, member: member)
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
  let model: RepresentativesFeatureModel
  let member: Member

  private var presentation: MemberPresentation {
    MemberPresentation(member: member)
  }

  var body: some View {
    NavigationLink {
      MemberDetailView(model: model.detailModel(for: member))
    } label: {
      VStack(alignment: .leading, spacing: 3) {
        Text(member.name)
          .font(.headline)
        Text("\(presentation.roleName) · \(presentation.partyAndJurisdiction)")
          .font(.subheadline)
          .foregroundStyle(.secondary)
      }
      .padding(.vertical, 4)
    }
    .accessibilityElement(children: .combine)
    .accessibilityLabel("View details for \(member.name)")
    .accessibilityHint("Shows voting, sponsored, and cosponsored records")
    .accessibilityIdentifier("representative-detail-\(member.bioguideID)")
  }
}
