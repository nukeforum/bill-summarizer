import Foundation
import ICClients

public actor BundledZipDistrictLookup {
  private let loadData: @Sendable () throws -> Data
  private var cachedEntries: [String: ZipDistrictEntry]?

  public init(
    bundle: Bundle = .main,
    resourceName: String = "zip_to_cd"
  ) {
    let resourceURL = bundle.url(forResource: resourceName, withExtension: "json")
    loadData = {
      guard let resourceURL else { throw BundledZipDistrictLookupError.missingResource }
      return try Data(contentsOf: resourceURL)
    }
  }

  public init(data: Data) {
    loadData = { data }
  }

  public func lookup(_ zipCode: String) throws -> ZipDistrictMatch {
    let entries = try loadEntries()
    guard let entry = entries[zipCode] else { return .notFound }
    guard entry.districts.count > 1 else {
      return .single(
        stateCode: entry.state,
        district: entry.districts.first ?? 0
      )
    }
    return .multiple(stateCode: entry.state, districts: entry.districts)
  }

  private func loadEntries() throws -> [String: ZipDistrictEntry] {
    if let cachedEntries { return cachedEntries }
    let entries = try JSONDecoder().decode(
      [String: ZipDistrictEntry].self,
      from: loadData()
    )
    cachedEntries = entries
    return entries
  }
}

private struct ZipDistrictEntry: Decodable {
  let state: String
  let districts: [Int]
}

public enum BundledZipDistrictLookupError: Error, Equatable, Sendable {
  case missingResource
}

extension ZipDistrictClient {
  public static func live(lookup: BundledZipDistrictLookup = BundledZipDistrictLookup()) -> Self {
    Self(lookup: { try await lookup.lookup($0) })
  }
}
