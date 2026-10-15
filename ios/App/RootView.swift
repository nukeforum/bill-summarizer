import ICFeatures
import SwiftUI

struct RootView: View {
  let model: BillsFeatureModel
  let representativesModel: RepresentativesFeatureModel
  let calendarModel: CalendarFeatureModel

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

      CalendarView(model: calendarModel)
        .tabItem {
          Label("Calendar", systemImage: "calendar")
        }
    }
  }
}
