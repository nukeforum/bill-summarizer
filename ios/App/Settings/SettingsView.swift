import ICFeatures
import ICModels
import SwiftUI

struct SettingsView: View {
  @Bindable var model: SettingsFeatureModel
  let representativesModel: RepresentativesFeatureModel
  let onOpenCalendar: () -> Void

  @Environment(\.dismiss) private var dismiss
  @State private var showsForgetConfirmation = false

  var body: some View {
    NavigationStack {
      Form {
        appearanceSection
        congressSection
        representativesSection
        dataSection
        aboutSection
      }
      .navigationTitle("Settings")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .confirmationAction) {
          Button("Done") { dismiss() }
        }
      }
    }
    .accessibilityIdentifier("settings-screen")
    .task {
      guard !model.isLoaded else { return }
      await model.load()
    }
    .confirmationDialog(
      "Forget your representatives?",
      isPresented: $showsForgetConfirmation,
      titleVisibility: .visible
    ) {
      Button("Forget Representatives", role: .destructive) {
        Task {
          if await model.forgetRepresentatives() {
            representativesModel.changeLocation()
          }
        }
      }
      Button("Cancel", role: .cancel) {}
    } message: {
      Text("You can choose your location again from the Representatives tab.")
    }
    .alert(
      "Setting Not Saved",
      isPresented: Binding(
        get: { model.errorMessage != nil },
        set: { if !$0 { model.dismissError() } }
      )
    ) {
      Button("OK") { model.dismissError() }
    } message: {
      Text(model.errorMessage ?? "Please try again.")
    }
  }

  private var appearanceSection: some View {
    Section("Appearance") {
      Picker(
        "Theme",
        selection: Binding(
          get: { model.appearance },
          set: { preference in
            Task { await model.setAppearance(preference) }
          }
        )
      ) {
        ForEach(AppearancePreference.allCases, id: \.self) { preference in
          Text(preference.displayName).tag(preference)
        }
      }
      .pickerStyle(.segmented)
      .accessibilityIdentifier("appearance-picker")
    }
  }

  private var congressSection: some View {
    Section("Congress") {
      Button {
        dismiss()
        onOpenCalendar()
      } label: {
        SettingsRow(
          title: "Congress Calendar",
          subtitle: "When the House and Senate are in session.",
          systemImage: "calendar"
        )
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("settings-calendar")
    }
  }

  private var representativesSection: some View {
    Section {
      Text(
        "The app stores only your representative IDs on this device. Your home address and ZIP code are never saved."
      )
      .font(.subheadline)
      .foregroundStyle(.secondary)

      Button("Forget My Representatives", role: .destructive) {
        showsForgetConfirmation = true
      }
      .disabled(!model.hasSavedRepresentatives)
      .accessibilityIdentifier("settings-forget-representatives")
    } header: {
      Text("Your Representatives")
    }
  }

  private var dataSection: some View {
    Section("Data") {
      Text(
        "Bill information comes from Congress.gov and is refreshed daily by an open-source workflow."
      )
      .font(.subheadline)
      .foregroundStyle(.secondary)

      Link(destination: URL(string: "https://www.congress.gov/")!) {
        SettingsRow(
          title: "Congress.gov",
          systemImage: "building.columns",
          showsExternalLink: true
        )
      }
    }
  }

  private var aboutSection: some View {
    Section("About") {
      Text(
        "Informed Citizen is an independent, third-party app and is not affiliated with or endorsed by the U.S. government."
      )
      .font(.subheadline)
      .foregroundStyle(.secondary)

      Link(destination: URL(string: "https://nukeforum.github.io/bill-summarizer/privacy.html")!) {
        SettingsRow(
          title: "Privacy Policy",
          systemImage: "hand.raised",
          showsExternalLink: true
        )
      }
      .accessibilityIdentifier("settings-privacy-policy")

      Link(destination: URL(string: "https://github.com/nukeforum/bill-summarizer")!) {
        SettingsRow(
          title: "Source Code",
          systemImage: "chevron.left.forwardslash.chevron.right",
          showsExternalLink: true
        )
      }
    }
  }
}

private struct SettingsRow: View {
  let title: String
  var subtitle: String?
  let systemImage: String
  var showsExternalLink = false

  var body: some View {
    Label {
      HStack(alignment: .firstTextBaseline) {
        VStack(alignment: .leading, spacing: 3) {
          Text(title)
          if let subtitle {
            Text(subtitle)
              .font(.subheadline)
              .foregroundStyle(.secondary)
          }
        }
        Spacer()
        if showsExternalLink {
          Image(systemName: "arrow.up.right")
            .font(.caption)
            .foregroundStyle(.secondary)
            .accessibilityHidden(true)
        }
      }
    } icon: {
      Image(systemName: systemImage)
    }
  }
}

private extension AppearancePreference {
  var displayName: String {
    switch self {
    case .system: "System"
    case .light: "Light"
    case .dark: "Dark"
    }
  }
}
