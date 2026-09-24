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
      } operation: {
        RepresentativesFeatureModel()
      }
    )
  }

  var body: some Scene {
    WindowGroup {
      RootView(model: billsModel, representativesModel: representativesModel)
        .tint(ICDesign.accent)
        .presentsWebDestinationsInApp()
    }
  }
}
