import Dependencies
import ICClients
import ICFeatures
import ICModels
import Testing

@MainActor
@Suite("Settings feature")
struct SettingsFeatureModelTests {
  @Test("Loads appearance and saved-representative status from injected clients")
  func load() async {
    await withDependencies {
      $0.appearancePreferenceClient = AppearancePreferenceClient(
        load: { .dark },
        save: { _ in }
      )
      $0.savedRepresentativesClient = .saved
    } operation: {
      let model = SettingsFeatureModel()
      await model.load()

      #expect(model.appearance == .dark)
      #expect(model.hasSavedRepresentatives)
      #expect(model.isLoaded)
    }
  }

  @Test("Persists appearance through its injected client")
  func setAppearance() async {
    let recorder = AppearanceRecorder()

    await withDependencies {
      $0.appearancePreferenceClient = AppearancePreferenceClient(
        load: { .system },
        save: { await recorder.save($0) }
      )
      $0.savedRepresentativesClient = .empty
    } operation: {
      let model = SettingsFeatureModel()
      await model.load()
      await model.setAppearance(.light)

      #expect(model.appearance == .light)
      #expect(await recorder.preference == .light)
      #expect(model.errorMessage == nil)
    }
  }

  @Test("Restores appearance and explains a persistence failure")
  func appearanceFailure() async {
    await withDependencies {
      $0.appearancePreferenceClient = AppearancePreferenceClient(
        load: { .system },
        save: { _ in throw SettingsFailure() }
      )
      $0.savedRepresentativesClient = .empty
    } operation: {
      let model = SettingsFeatureModel()
      await model.load()
      await model.setAppearance(.dark)

      #expect(model.appearance == .system)
      #expect(model.errorMessage == "We couldn’t save that appearance setting.")
    }
  }

  @Test("Forgets saved representatives through its injected client")
  func forgetRepresentatives() async {
    let recorder = ClearRecorder()

    await withDependencies {
      $0.appearancePreferenceClient = .previewValue
      $0.savedRepresentativesClient = SavedRepresentativesClient(
        load: { SavedRepresentativesClient.sampleSelection },
        save: { _ in },
        clear: { await recorder.clear() }
      )
    } operation: {
      let model = SettingsFeatureModel()
      await model.load()

      #expect(await model.forgetRepresentatives())
      #expect(!model.hasSavedRepresentatives)
      #expect(await recorder.didClear)
    }
  }

  @Test("Keeps saved status and explains a removal failure")
  func forgetFailure() async {
    await withDependencies {
      $0.appearancePreferenceClient = .previewValue
      $0.savedRepresentativesClient = SavedRepresentativesClient(
        load: { SavedRepresentativesClient.sampleSelection },
        save: { _ in },
        clear: { throw SettingsFailure() }
      )
    } operation: {
      let model = SettingsFeatureModel()
      await model.load()

      #expect(!(await model.forgetRepresentatives()))
      #expect(model.hasSavedRepresentatives)
      #expect(model.errorMessage == "We couldn’t remove your saved representatives.")
    }
  }
}

private actor AppearanceRecorder {
  private(set) var preference: AppearancePreference?

  func save(_ preference: AppearancePreference) {
    self.preference = preference
  }
}

private actor ClearRecorder {
  private(set) var didClear = false

  func clear() {
    didClear = true
  }
}

private struct SettingsFailure: Error {}

private extension SavedRepresentativesClient {
  static let sampleSelection = SavedRepresentativesSelection(
    stateCode: "AZ",
    district: 1,
    bioguideIDs: ["K000377", "G000574", "S001183"]
  )

  static let saved = Self(
    load: { sampleSelection },
    save: { _ in },
    clear: {}
  )

  static let empty = Self(
    load: { nil },
    save: { _ in },
    clear: {}
  )
}
