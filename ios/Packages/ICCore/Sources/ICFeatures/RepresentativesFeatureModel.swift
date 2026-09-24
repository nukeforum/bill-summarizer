import Dependencies
import ICClients
import ICModels
import Observation

public struct RepresentativesSnapshot: Equatable, Sendable {
  public let selection: SavedRepresentativesSelection
  public let house: [Member]
  public let senators: [Member]

  public init(
    selection: SavedRepresentativesSelection,
    house: [Member],
    senators: [Member]
  ) {
    self.selection = selection
    self.house = house
    self.senators = senators
  }
}

@Observable
@MainActor
public final class RepresentativesFeatureModel {
  public enum State: Equatable, Sendable {
    case loading
    case choosing(message: String?)
    case loaded(RepresentativesSnapshot)
    case staleSavedRepresentatives
    case failed(message: String)
  }

  public private(set) var state: State = .loading
  public private(set) var selectedState: String?
  public private(set) var selectedDistrict: Int?

  @ObservationIgnored
  @Dependency(\.membersClient) private var membersClient

  @ObservationIgnored
  @Dependency(\.savedRepresentativesClient) private var savedRepresentativesClient

  @ObservationIgnored
  private var index: MembersIndex?

  public init() {}

  public var availableStates: [String] {
    guard let index else { return [] }
    return Array(Set(index.members.map(\.state))).sorted()
  }

  public var availableDistricts: [Int] {
    guard let index, let selectedState else { return [] }
    return Array(
      Set(
        index.members
          .filter { $0.state == selectedState && $0.chamber == .house }
          .compactMap(\.district)
      )
    ).sorted()
  }

  public var canSaveSelection: Bool {
    selectedState != nil && selectedDistrict != nil
  }

  public func load() async {
    state = .loading
    do {
      let fetchedIndex = try await membersClient.fetchCurrent()
      index = fetchedIndex
      guard let saved = try await savedRepresentativesClient.load() else {
        state = .choosing(message: nil)
        return
      }
      state = resolve(saved: saved, in: fetchedIndex)
    } catch is CancellationError {
      return
    } catch {
      state = .failed(
        message: "We couldn’t load representatives. Check your connection and try again.")
    }
  }

  public func selectState(_ stateCode: String) {
    selectedState = stateCode
    selectedDistrict = nil
    if availableDistricts.count == 1 {
      selectedDistrict = availableDistricts[0]
    }
    state = .choosing(message: nil)
  }

  public func selectDistrict(_ district: Int) {
    selectedDistrict = district
    state = .choosing(message: nil)
  }

  public func saveSelection() async {
    guard
      let index,
      let stateCode = selectedState,
      let district = selectedDistrict
    else { return }

    let senators = members(in: index, chamber: .senate, state: stateCode)
    let house = members(in: index, chamber: .house, state: stateCode, district: district)
    let representatives = senators + house
    guard !representatives.isEmpty, !house.isEmpty else {
      state = .choosing(message: "We couldn’t locate a House representative for that district.")
      return
    }

    let selection = SavedRepresentativesSelection(
      stateCode: stateCode,
      district: district,
      bioguideIDs: Set(representatives.map(\.bioguideID))
    )
    do {
      try await savedRepresentativesClient.save(selection)
      state = .loaded(
        RepresentativesSnapshot(selection: selection, house: house, senators: senators)
      )
    } catch {
      state = .choosing(message: "We couldn’t save those representatives. Please try again.")
    }
  }

  public func changeLocation() {
    selectedState = nil
    selectedDistrict = nil
    state = .choosing(message: nil)
  }

  public func removeSavedRepresentatives() async {
    do {
      try await savedRepresentativesClient.clear()
      changeLocation()
    } catch {
      state = .failed(message: "We couldn’t remove the saved representatives.")
    }
  }

  private func resolve(
    saved: SavedRepresentativesSelection,
    in index: MembersIndex
  ) -> State {
    let representatives = index.members.filter { saved.bioguideIDs.contains($0.bioguideID) }
    guard Set(representatives.map(\.bioguideID)) == saved.bioguideIDs else {
      return .staleSavedRepresentatives
    }
    return .loaded(
      RepresentativesSnapshot(
        selection: saved,
        house: representatives.filter { $0.chamber == .house }.sorted(by: memberNameOrder),
        senators: representatives.filter { $0.chamber == .senate }.sorted(by: memberNameOrder)
      )
    )
  }

  private func members(
    in index: MembersIndex,
    chamber: MemberChamber,
    state: String,
    district: Int? = nil
  ) -> [Member] {
    index.members
      .filter { member in
        member.chamber == chamber && member.state == state
          && (district == nil || member.district == district)
      }
      .sorted(by: memberNameOrder)
  }

  private func memberNameOrder(_ lhs: Member, _ rhs: Member) -> Bool {
    lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
  }
}
