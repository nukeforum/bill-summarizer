import Foundation
import ICClients
import ICModels

public actor SavedRepresentativesStore {
  private let defaults: UserDefaults
  private let key: String
  private let decoder = JSONDecoder()
  private let encoder = JSONEncoder()

  public init(
    suiteName: String = "com.informedcitizen.ios.saved-representatives",
    key: String = "selection"
  ) {
    guard let defaults = UserDefaults(suiteName: suiteName) else {
      preconditionFailure("Could not create the saved-representatives defaults suite")
    }
    self.defaults = defaults
    self.key = key
  }

  public func load() throws -> SavedRepresentativesSelection? {
    guard let data = defaults.data(forKey: key) else { return nil }
    return try decoder.decode(SavedRepresentativesSelection.self, from: data)
  }

  public func save(_ selection: SavedRepresentativesSelection) throws {
    defaults.set(try encoder.encode(selection), forKey: key)
  }

  public func clear() {
    defaults.removeObject(forKey: key)
  }
}

extension SavedRepresentativesClient {
  public static func live(store: SavedRepresentativesStore = SavedRepresentativesStore()) -> Self {
    Self(
      load: { try await store.load() },
      save: { try await store.save($0) },
      clear: { await store.clear() }
    )
  }
}
