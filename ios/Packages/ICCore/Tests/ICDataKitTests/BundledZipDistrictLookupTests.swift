import Foundation
import ICClients
import ICDataKit
import Testing

@Suite("Bundled ZIP district lookup")
struct BundledZipDistrictLookupTests {
  @Test("Decodes single, multiple, at-large, and missing ZIP matches")
  func decodesCrosswalk() async throws {
    let url = try #require(
      Bundle.module.url(forResource: "zip_to_cd", withExtension: "json")
    )
    let data = try Data(contentsOf: url)
    let lookup = BundledZipDistrictLookup(data: data)

    #expect(try await lookup.lookup("85001") == .single(stateCode: "AZ", district: 3))
    #expect(
      try await lookup.lookup("36401")
        == .multiple(stateCode: "AL", districts: [1, 2]))
    #expect(try await lookup.lookup("00601") == .single(stateCode: "PR", district: 0))
    #expect(try await lookup.lookup("00000") == .notFound)
  }

  @Test("Reports a missing bundled resource")
  func missingResource() async {
    let lookup = BundledZipDistrictLookup(
      bundle: .module,
      resourceName: "missing_zip_crosswalk"
    )

    await #expect(throws: BundledZipDistrictLookupError.missingResource) {
      try await lookup.lookup("85001")
    }
  }
}
