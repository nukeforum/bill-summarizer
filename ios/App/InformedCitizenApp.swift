import Dependencies
import ICClients
import ICDataKit
import ICDesign
import ICFeatures
import SwiftUI

@main
@MainActor
struct InformedCitizenApp: App {
  @State private var billsModel: BillsFeatureModel
  @State private var representativesModel: RepresentativesFeatureModel
  @State private var calendarModel: CalendarFeatureModel

  init() {
    let screenshotMode = ProcessInfo.processInfo.arguments.contains("-SCREENSHOT_MODE")
    _billsModel = State(
      initialValue: withDependencies {
        $0.billsClient = screenshotMode ? .previewValue : .live()
      } operation: {
        BillsFeatureModel()
      }
    )
    _representativesModel = State(
      initialValue: withDependencies {
        $0.membersClient = screenshotMode ? .previewValue : .live()
        $0.savedRepresentativesClient = screenshotMode ? .previewValue : .live()
        $0.zipDistrictClient = screenshotMode ? .previewValue : .live()
      } operation: {
        RepresentativesFeatureModel()
      }
    )
    _calendarModel = State(
      initialValue: withDependencies {
        $0.calendarClient = screenshotMode ? .previewValue : .live()
        $0.membersClient = screenshotMode ? .previewValue : .live()
        $0.savedRepresentativesClient = screenshotMode ? .previewValue : .live()
        if screenshotMode,
          let previewDate = CalendarPresentation.date(from: "2026-10-14")
        {
          $0.date = .constant(previewDate)
        }
      } operation: {
        CalendarFeatureModel()
      }
    )
  }

  var body: some Scene {
    WindowGroup {
      RootView(
        model: billsModel,
        representativesModel: representativesModel,
        calendarModel: calendarModel
      )
      .tint(ICDesign.accent)
      .presentsWebDestinationsInApp()
    }
  }
}
