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

  init() {
    let screenshotMode = ProcessInfo.processInfo.arguments.contains("-SCREENSHOT_MODE")
    _billsModel = State(
      initialValue: withDependencies {
        $0.billsClient = screenshotMode ? .previewValue : .live()
      } operation: {
        BillsFeatureModel()
      }
    )
  }

  var body: some Scene {
    WindowGroup {
      RootView(model: billsModel)
        .tint(ICDesign.accent)
    }
  }
}
