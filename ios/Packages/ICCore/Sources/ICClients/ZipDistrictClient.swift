import Dependencies

public enum ZipDistrictMatch: Equatable, Sendable {
  case single(stateCode: String, district: Int)
  case multiple(stateCode: String, districts: [Int])
  case notFound
}

public struct ZipDistrictClient: Sendable {
  public var lookup: @Sendable (String) async throws -> ZipDistrictMatch

  public init(lookup: @escaping @Sendable (String) async throws -> ZipDistrictMatch) {
    self.lookup = lookup
  }
}

extension ZipDistrictClient: DependencyKey {
  public static let liveValue = ZipDistrictClient { _ in
    throw UnimplementedZipDistrictClientError()
  }

  public static let previewValue = ZipDistrictClient { zipCode in
    switch zipCode {
    case "85001": .single(stateCode: "AZ", district: 1)
    case "85002": .multiple(stateCode: "AZ", districts: [1, 2])
    default: .notFound
    }
  }

  public static let testValue = ZipDistrictClient { _ in
    throw UnimplementedZipDistrictClientError()
  }
}

extension DependencyValues {
  public var zipDistrictClient: ZipDistrictClient {
    get { self[ZipDistrictClient.self] }
    set { self[ZipDistrictClient.self] = newValue }
  }
}

public struct UnimplementedZipDistrictClientError: Error, Equatable, Sendable {
  public init() {}
}
