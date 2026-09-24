import Foundation
import ICDataKit
import ICModels
import Testing

@Suite("Saved representatives store")
struct SavedRepresentativesStoreTests {
  @Test("Persists and clears a selection in an isolated suite")
  func roundTrip() async throws {
    let suiteName = "SavedRepresentativesStoreTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }
    let store = SavedRepresentativesStore(suiteName: suiteName)
    let selection = SavedRepresentativesSelection(
      stateCode: "AZ",
      district: 1,
      bioguideIDs: ["K000377", "G000574", "S001183"]
    )

    #expect(try await store.load() == nil)
    try await store.save(selection)
    #expect(try await store.load() == selection)
    await store.clear()
    #expect(try await store.load() == nil)
  }
}
