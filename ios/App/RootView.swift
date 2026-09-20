import ICFeatures
import SwiftUI

struct RootView: View {
  let model: BillsFeatureModel

  var body: some View {
    TabView {
      BillsView(model: model)
        .tabItem {
          Label("Bills", systemImage: "doc.text")
        }

      NavigationStack {
        ContentUnavailableView(
          "Representatives",
          systemImage: "building.columns",
          description: Text("Representative lookup is coming in the next port milestone.")
        )
        .navigationTitle("Representatives")
      }
      .tabItem {
        Label("Reps", systemImage: "building.columns")
      }
    }
  }
}
