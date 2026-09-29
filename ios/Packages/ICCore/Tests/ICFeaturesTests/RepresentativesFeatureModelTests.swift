import Dependencies
import ICClients
import ICFeatures
import ICModels
import Testing

@MainActor
@Suite("Representatives feature")
struct RepresentativesFeatureModelTests {
  @Test("Derives states and districts from the published members")
  func derivesLocations() async {
    await withDependencies {
      $0.membersClient = MembersClient(fetchCurrent: { .sample })
      $0.savedRepresentativesClient = .empty
    } operation: {
      let model = RepresentativesFeatureModel()

      await model.load()

      #expect(model.state == .choosing(message: nil))
      #expect(model.availableStates == ["AZ"])
      model.selectState("AZ")
      #expect(model.availableDistricts == [1])
      #expect(model.selectedDistrict == 1)
      #expect(model.canSaveSelection)
    }
  }

  @Test("Saves the exact published delegation through the injected client")
  func savesSelection() async {
    let recorder = SavedSelectionRecorder()

    await withDependencies {
      $0.membersClient = MembersClient(fetchCurrent: { .sample })
      $0.savedRepresentativesClient = SavedRepresentativesClient(
        load: { nil },
        save: { await recorder.save($0) },
        clear: {}
      )
    } operation: {
      let model = RepresentativesFeatureModel()
      await model.load()
      model.selectState("AZ")
      await model.saveSelection()

      let saved = await recorder.selection
      #expect(saved?.stateCode == "AZ")
      #expect(saved?.district == 1)
      #expect(saved?.bioguideIDs == ["K000377", "G000574", "S001183"])
      guard case .loaded(let snapshot) = model.state else {
        Issue.record("Expected representatives to be loaded")
        return
      }
      #expect(snapshot.house.map(\.bioguideID) == ["S001183"])
      #expect(snapshot.senators.map(\.bioguideID) == ["K000377", "G000574"])
    }
  }

  @Test("Surfaces a stale saved delegation instead of silently changing it")
  func staleSelection() async {
    let selection = SavedRepresentativesSelection(
      stateCode: "AZ",
      district: 1,
      bioguideIDs: ["K000377", "G000574", "S001183", "MISSING"]
    )

    await withDependencies {
      $0.membersClient = MembersClient(fetchCurrent: { .sample })
      $0.savedRepresentativesClient = SavedRepresentativesClient(
        load: { selection },
        save: { _ in },
        clear: {}
      )
    } operation: {
      let model = RepresentativesFeatureModel()
      await model.load()
      #expect(model.state == .staleSavedRepresentatives)
    }
  }

  @Test("A single ZIP match asks for confirmation before saving")
  func singleZipMatch() async {
    let recorder = SavedSelectionRecorder()

    await withDependencies {
      $0.membersClient = MembersClient(fetchCurrent: { .sample })
      $0.savedRepresentativesClient = SavedRepresentativesClient(
        load: { nil },
        save: { await recorder.save($0) },
        clear: {}
      )
      $0.zipDistrictClient = ZipDistrictClient { _ in
        .single(stateCode: "AZ", district: 1)
      }
    } operation: {
      let model = RepresentativesFeatureModel()
      await model.load()
      model.setZipCode("85001")
      await model.lookUpZip()

      #expect(model.selectedState == "AZ")
      #expect(model.selectedDistrict == 1)
      #expect(model.zipLookupStatus == .matched(stateCode: "AZ", district: 1))
      #expect(model.showsZipConfirmation)
      #expect(await recorder.selection == nil)

      await model.confirmZipSelection()

      #expect(await recorder.selection?.stateCode == "AZ")
      #expect(await recorder.selection?.district == 1)
    }
  }

  @Test("A ZIP spanning districts requires a manual district choice")
  func multipleZipMatch() async {
    await withDependencies {
      $0.membersClient = MembersClient(fetchCurrent: { .sample })
      $0.savedRepresentativesClient = .empty
      $0.zipDistrictClient = ZipDistrictClient { _ in
        .multiple(stateCode: "AZ", districts: [2, 1])
      }
    } operation: {
      let model = RepresentativesFeatureModel()
      await model.load()
      model.setZipCode("85002")
      await model.lookUpZip()

      #expect(model.locationSelectionMode == .district)
      #expect(model.selectedState == "AZ")
      #expect(model.selectedDistrict == nil)
      #expect(model.availableDistricts == [1, 2])
      #expect(
        model.zipLookupStatus == .multiple(stateCode: "AZ", districts: [1, 2]))
      #expect(!model.canSaveSelection)

      model.selectDistrict(1)
      #expect(model.selectedDistrict == 1)
      #expect(model.canSaveSelection)
    }
  }

  @Test("ZIP input is digits-only and misses do not retain a stale location")
  func zipInputAndMiss() async {
    await withDependencies {
      $0.membersClient = MembersClient(fetchCurrent: { .sample })
      $0.savedRepresentativesClient = .empty
      $0.zipDistrictClient = ZipDistrictClient { _ in .notFound }
    } operation: {
      let model = RepresentativesFeatureModel()
      await model.load()
      model.selectState("AZ")
      model.setZipCode("85a0019")
      #expect(model.zipCode == "85001")

      await model.lookUpZip()

      #expect(model.zipLookupStatus == .notFound)
      #expect(model.selectedState == nil)
      #expect(model.selectedDistrict == nil)
    }
  }
}

private actor SavedSelectionRecorder {
  private(set) var selection: SavedRepresentativesSelection?

  func save(_ selection: SavedRepresentativesSelection) {
    self.selection = selection
  }
}

extension SavedRepresentativesClient {
  fileprivate static let empty = Self(
    load: { nil },
    save: { _ in },
    clear: {}
  )
}
