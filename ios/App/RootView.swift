import ICFeatures
import SwiftUI

struct RootView: View {
  let model: BillsFeatureModel
  let representativesModel: RepresentativesFeatureModel

  var body: some View {
    TabView {
      BillsView(model: model)
        .tabItem {
          Label("Bills", systemImage: "doc.text")
        }

      RepresentativesView(model: representativesModel)
        .tabItem {
          Label("Reps", systemImage: "building.columns")
        }
    }
  }
}
