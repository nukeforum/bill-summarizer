import ICFeatures
import ICModels
import SwiftUI

struct RootView: View {
  let model: BillsFeatureModel
  let representativesModel: RepresentativesFeatureModel
  let calendarModel: CalendarFeatureModel
  let settingsModel: SettingsFeatureModel

  @State private var selection = RootTab.bills

  var body: some View {
    TabView(selection: $selection) {
      BillsView(
        model: model,
        settingsModel: settingsModel,
        representativesModel: representativesModel,
        onOpenCalendar: { selection = .calendar }
      )
        .tabItem {
          Label("Bills", systemImage: "doc.text")
        }
        .tag(RootTab.bills)

      RepresentativesView(model: representativesModel)
        .tabItem {
          Label("Reps", systemImage: "building.columns")
        }
        .tag(RootTab.representatives)

      CalendarView(model: calendarModel)
        .tabItem {
          Label("Calendar", systemImage: "calendar")
        }
        .tag(RootTab.calendar)
    }
    .preferredColorScheme(settingsModel.appearance.colorScheme)
    .task {
      guard !settingsModel.isLoaded else { return }
      await settingsModel.load()
    }
  }
}

private enum RootTab: Hashable {
  case bills
  case representatives
  case calendar
}

private extension AppearancePreference {
  var colorScheme: ColorScheme? {
    switch self {
    case .system: nil
    case .light: .light
    case .dark: .dark
    }
  }
}
