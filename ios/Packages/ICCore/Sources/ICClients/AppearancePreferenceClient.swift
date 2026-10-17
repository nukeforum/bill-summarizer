import Dependencies
import ICModels

public struct AppearancePreferenceClient: Sendable {
  public var load: @Sendable () async throws -> AppearancePreference
  public var save: @Sendable (AppearancePreference) async throws -> Void

  public init(
    load: @escaping @Sendable () async throws -> AppearancePreference,
    save: @escaping @Sendable (AppearancePreference) async throws -> Void
  ) {
    self.load = load
    self.save = save
  }
}

extension AppearancePreferenceClient: DependencyKey {
  public static let liveValue = AppearancePreferenceClient(
    load: { throw UnimplementedAppearancePreferenceClientError() },
    save: { _ in throw UnimplementedAppearancePreferenceClientError() }
  )

  public static let previewValue = AppearancePreferenceClient(
    load: { .system },
    save: { _ in }
  )

  public static let testValue = AppearancePreferenceClient(
    load: { throw UnimplementedAppearancePreferenceClientError() },
    save: { _ in throw UnimplementedAppearancePreferenceClientError() }
  )
}

extension DependencyValues {
  public var appearancePreferenceClient: AppearancePreferenceClient {
    get { self[AppearancePreferenceClient.self] }
    set { self[AppearancePreferenceClient.self] = newValue }
  }
}

public struct UnimplementedAppearancePreferenceClientError: Error, Equatable, Sendable {
  public init() {}
}
