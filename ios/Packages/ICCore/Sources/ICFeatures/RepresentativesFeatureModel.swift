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

public enum LocationSelectionMode: String, CaseIterable, Equatable, Sendable {
  case zipCode
  case district
}

public enum ZipLookupStatus: Equatable, Sendable {
  case idle
  case lookingUp
  case invalid
  case notFound
  case matched(stateCode: String, district: Int)
  case multiple(stateCode: String, districts: [Int])
  case failed
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
  public private(set) var locationSelectionMode: LocationSelectionMode = .zipCode
  public private(set) var zipCode = ""
  public private(set) var zipLookupStatus: ZipLookupStatus = .idle
  public private(set) var showsZipConfirmation = false

  @ObservationIgnored
  @Dependency(\.membersClient) private var membersClient

  @ObservationIgnored
  @Dependency(\.savedRepresentativesClient) private var savedRepresentativesClient

  @ObservationIgnored
  @Dependency(\.zipDistrictClient) private var zipDistrictClient

  @ObservationIgnored
  private var index: MembersIndex?

  @ObservationIgnored
  private var zipCandidateDistricts: [Int] = []

  public init() {}

  public var availableStates: [String] {
    guard let index else { return [] }
    return Array(Set(index.members.map(\.state))).sorted()
  }

  public var availableDistricts: [Int] {
    guard let index, let selectedState else { return [] }
    if !zipCandidateDistricts.isEmpty {
      return zipCandidateDistricts
    }
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

  public var canLookUpZip: Bool {
    zipCode.count == 5 && zipCode.allSatisfy(\.isNumber)
      && zipLookupStatus != .lookingUp
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
    zipCandidateDistricts = []
    zipLookupStatus = .idle
    if availableDistricts.count == 1 {
      selectedDistrict = availableDistricts[0]
    }
    state = .choosing(message: nil)
  }

  public func selectDistrict(_ district: Int) {
    selectedDistrict = district
    if case .multiple = zipLookupStatus {
      zipLookupStatus = .idle
    }
    state = .choosing(message: nil)
  }

  public func setLocationSelectionMode(_ mode: LocationSelectionMode) {
    locationSelectionMode = mode
  }

  public func setZipCode(_ input: String) {
    let sanitized = String(input.filter(\.isNumber).prefix(5))
    guard sanitized != zipCode else { return }
    zipCode = sanitized
    selectedState = nil
    selectedDistrict = nil
    zipCandidateDistricts = []
    zipLookupStatus = .idle
    showsZipConfirmation = false
  }

  public func lookUpZip() async {
    guard canLookUpZip else {
      zipLookupStatus = .invalid
      return
    }

    selectedState = nil
    selectedDistrict = nil
    zipCandidateDistricts = []
    showsZipConfirmation = false
    zipLookupStatus = .lookingUp
    do {
      switch try await zipDistrictClient.lookup(zipCode) {
      case .single(let stateCode, let district):
        selectedState = stateCode
        selectedDistrict = district
        zipCandidateDistricts = []
        zipLookupStatus = .matched(stateCode: stateCode, district: district)
        showsZipConfirmation = true

      case .multiple(let stateCode, let districts):
        selectedState = stateCode
        selectedDistrict = nil
        zipCandidateDistricts = districts.sorted()
        zipLookupStatus = .multiple(stateCode: stateCode, districts: districts.sorted())
        locationSelectionMode = .district

      case .notFound:
        selectedState = nil
        selectedDistrict = nil
        zipCandidateDistricts = []
        zipLookupStatus = .notFound
      }
      state = .choosing(message: nil)
    } catch is CancellationError {
      return
    } catch {
      zipLookupStatus = .failed
    }
  }

  public func confirmZipSelection() async {
    showsZipConfirmation = false
    await saveSelection()
  }

  public func dismissZipConfirmation() {
    showsZipConfirmation = false
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
    zipCode = ""
    zipCandidateDistricts = []
    zipLookupStatus = .idle
    showsZipConfirmation = false
    locationSelectionMode = .zipCode
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

  public func detailModel(for member: Member) -> MemberDetailFeatureModel {
    withDependencies {
      $0.membersClient = membersClient
    } operation: {
      MemberDetailFeatureModel(member: member)
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
