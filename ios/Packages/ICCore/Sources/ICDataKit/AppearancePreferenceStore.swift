import Foundation
import ICClients
import ICModels

public actor AppearancePreferenceStore {
  private let defaults: UserDefaults
  private let key: String

  public init(
    suiteName: String = "com.informedcitizen.ios.appearance",
    key: String = "preference"
  ) {
    guard let defaults = UserDefaults(suiteName: suiteName) else {
      preconditionFailure("Could not create the appearance preferences suite")
    }
    self.defaults = defaults
    self.key = key
  }

  public func load() -> AppearancePreference {
    guard
      let rawValue = defaults.string(forKey: key),
      let preference = AppearancePreference(rawValue: rawValue)
    else { return .system }
    return preference
  }

  public func save(_ preference: AppearancePreference) {
    defaults.set(preference.rawValue, forKey: key)
  }
}

extension AppearancePreferenceClient {
  public static func live(
    store: AppearancePreferenceStore = AppearancePreferenceStore()
  ) -> Self {
    Self(
      load: { await store.load() },
      save: { await store.save($0) }
    )
  }
}
