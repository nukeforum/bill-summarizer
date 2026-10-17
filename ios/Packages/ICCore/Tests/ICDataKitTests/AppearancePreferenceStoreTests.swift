import Foundation
import ICDataKit
import ICModels
import Testing

@Suite("Appearance preference store")
struct AppearancePreferenceStoreTests {
  @Test("Defaults to system and persists a preference in an isolated suite")
  func roundTrip() async throws {
    let suiteName = "AppearancePreferenceStoreTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }
    let store = AppearancePreferenceStore(suiteName: suiteName)

    #expect(await store.load() == .system)
    await store.save(.dark)
    #expect(await store.load() == .dark)
  }
}
