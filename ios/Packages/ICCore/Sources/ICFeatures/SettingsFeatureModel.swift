import Dependencies
import ICClients
import ICModels
import Observation

@Observable
@MainActor
public final class SettingsFeatureModel {
  public private(set) var appearance: AppearancePreference = .system
  public private(set) var hasSavedRepresentatives = false
  public private(set) var isLoaded = false
  public private(set) var errorMessage: String?

  @ObservationIgnored
  @Dependency(\.appearancePreferenceClient) private var appearancePreferenceClient

  @ObservationIgnored
  @Dependency(\.savedRepresentativesClient) private var savedRepresentativesClient

  public init() {}

  public func load() async {
    async let appearance = try? appearancePreferenceClient.load()
    async let selection = try? savedRepresentativesClient.load()
    let (loadedAppearance, loadedSelection) = await (appearance, selection)
    self.appearance = loadedAppearance ?? .system
    hasSavedRepresentatives = loadedSelection != nil
    isLoaded = true
  }

  public func setAppearance(_ preference: AppearancePreference) async {
    let previous = appearance
    appearance = preference
    errorMessage = nil
    do {
      try await appearancePreferenceClient.save(preference)
    } catch {
      appearance = previous
      errorMessage = "We couldn’t save that appearance setting."
    }
  }

  @discardableResult
  public func forgetRepresentatives() async -> Bool {
    errorMessage = nil
    do {
      try await savedRepresentativesClient.clear()
      hasSavedRepresentatives = false
      return true
    } catch {
      errorMessage = "We couldn’t remove your saved representatives."
      return false
    }
  }

  public func dismissError() {
    errorMessage = nil
  }
}
