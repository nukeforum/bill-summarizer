import Dependencies
import ICModels

public struct MembersClient: Sendable {
  public var fetchCurrent: @Sendable () async throws -> MembersIndex

  public init(fetchCurrent: @escaping @Sendable () async throws -> MembersIndex) {
    self.fetchCurrent = fetchCurrent
  }
}

extension MembersClient: DependencyKey {
  public static let liveValue = MembersClient(fetchCurrent: {
    throw UnimplementedMembersClientError()
  })

  public static let previewValue = MembersClient(fetchCurrent: {
    .sample
  })

  public static let testValue = MembersClient(fetchCurrent: {
    throw UnimplementedMembersClientError()
  })
}

extension DependencyValues {
  public var membersClient: MembersClient {
    get { self[MembersClient.self] }
    set { self[MembersClient.self] = newValue }
  }
}

public struct UnimplementedMembersClientError: Error, Equatable, Sendable {
  public init() {}
}

public struct SavedRepresentativesClient: Sendable {
  public var load: @Sendable () async throws -> SavedRepresentativesSelection?
  public var save: @Sendable (SavedRepresentativesSelection) async throws -> Void
  public var clear: @Sendable () async throws -> Void

  public init(
    load: @escaping @Sendable () async throws -> SavedRepresentativesSelection?,
    save: @escaping @Sendable (SavedRepresentativesSelection) async throws -> Void,
    clear: @escaping @Sendable () async throws -> Void
  ) {
    self.load = load
    self.save = save
    self.clear = clear
  }
}

extension SavedRepresentativesClient: DependencyKey {
  public static let liveValue = SavedRepresentativesClient(
    load: { throw UnimplementedSavedRepresentativesClientError() },
    save: { _ in throw UnimplementedSavedRepresentativesClientError() },
    clear: { throw UnimplementedSavedRepresentativesClientError() }
  )

  public static let previewValue = SavedRepresentativesClient(
    load: {
      SavedRepresentativesSelection(
        stateCode: "AZ",
        district: 1,
        bioguideIDs: ["K000377", "G000574", "S001183"]
      )
    },
    save: { _ in },
    clear: {}
  )

  public static let testValue = SavedRepresentativesClient(
    load: { throw UnimplementedSavedRepresentativesClientError() },
    save: { _ in throw UnimplementedSavedRepresentativesClientError() },
    clear: { throw UnimplementedSavedRepresentativesClientError() }
  )
}

extension DependencyValues {
  public var savedRepresentativesClient: SavedRepresentativesClient {
    get { self[SavedRepresentativesClient.self] }
    set { self[SavedRepresentativesClient.self] = newValue }
  }
}

public struct UnimplementedSavedRepresentativesClientError: Error, Equatable, Sendable {
  public init() {}
}
