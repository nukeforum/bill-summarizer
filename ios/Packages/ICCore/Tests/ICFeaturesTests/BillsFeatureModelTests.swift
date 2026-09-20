import Dependencies
import ICClients
import ICFeatures
import ICModels
import Testing

@MainActor
@Suite("Bills feature")
struct BillsFeatureModelTests {
  @Test("Loads injected bills without global state")
  func load() async {
    let snapshot = BillsSnapshot(
      generatedAt: "2026-09-19T15:43:14Z",
      congress: 119,
      votesCoverage: true,
      bills: Bill.samples
    )

    await withDependencies {
      $0.billsClient = BillsClient(fetch: { snapshot })
    } operation: {
      let model = BillsFeatureModel()
      await model.load()
      #expect(model.state == .loaded(snapshot))
    }
  }

  @Test("Search covers bill number and sponsor")
  func search() async {
    let snapshot = BillsSnapshot(
      generatedAt: "now",
      congress: 119,
      votesCoverage: false,
      bills: Bill.samples
    )

    await withDependencies {
      $0.billsClient = BillsClient(fetch: { snapshot })
    } operation: {
      let model = BillsFeatureModel()
      await model.load()

      model.searchQuery = "H.R. 1"
      #expect(model.visibleBills.map(\.id) == [Bill.sample.id])

      model.searchQuery = "Morgan"
      #expect(model.visibleBills.map(\.id) == ["s-42-119"])
    }
  }
}
