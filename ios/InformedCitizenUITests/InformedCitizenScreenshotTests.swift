import XCTest

final class InformedCitizenScreenshotTests: XCTestCase {
  @MainActor
  func testBillsFeedAndDetail() throws {
    let app = XCUIApplication()
    app.launchArguments += ["-SCREENSHOT_MODE"]
    app.launch()

    let billsList = app.descendants(matching: .any)["bills-list"]
    XCTAssertTrue(billsList.waitForExistence(timeout: 5))
    attachScreenshot(named: "01-bills-feed")

    let firstBill = app.buttons["bill-row-hr-1-119"]
    XCTAssertTrue(firstBill.waitForExistence(timeout: 5))
    firstBill.tap()
    XCTAssertTrue(app.navigationBars["H.R. 1"].waitForExistence(timeout: 5))
    attachScreenshot(named: "02-bill-detail")

    let rollCall = app.descendants(matching: .any)["roll-call-house-119-1-17"]
    XCTAssertTrue(rollCall.waitForExistence(timeout: 5))
    app.swipeUp()
    XCTAssertTrue(rollCall.isHittable)
    attachScreenshot(named: "03-bill-votes")
  }

  @MainActor
  private func attachScreenshot(named name: String) {
    let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
    attachment.name = name
    attachment.lifetime = .keepAlways
    add(attachment)
  }
}
