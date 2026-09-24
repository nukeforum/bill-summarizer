import XCTest

final class InformedCitizenScreenshotTests: XCTestCase {
  @MainActor
  func testCoreUserJourney() throws {
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

    app.navigationBars["H.R. 1"].buttons.element(boundBy: 0).tap()
    app.tabBars.buttons["Reps"].tap()
    XCTAssertTrue(app.staticTexts["Mark Kelly"].waitForExistence(timeout: 5))
    XCTAssertTrue(app.staticTexts["David Schweikert"].exists)
    attachScreenshot(named: "04-representatives")

    app.buttons["representative-detail-K000377"].tap()
    XCTAssertTrue(app.navigationBars["Mark Kelly"].waitForExistence(timeout: 5))
    XCTAssertTrue(app.staticTexts["Recent voting record (3)"].waitForExistence(timeout: 5))
    attachScreenshot(named: "05-member-voting-record")

    app.segmentedControls.buttons["Sponsored"].tap()
    let sponsoredBill = app.descendants(matching: .any)["member-legislation-s4478-119"]
    XCTAssertTrue(sponsoredBill.waitForExistence(timeout: 5))
    attachScreenshot(named: "06-member-sponsored-bills")

    app.navigationBars["Mark Kelly"].buttons.element(boundBy: 0).tap()
    app.navigationBars["Representatives"].buttons["Representative options"].tap()
    app.buttons["Change location"].tap()
    XCTAssertTrue(
      app.staticTexts[
        "Choose your state and congressional district to see your House representative and senators."
      ].waitForExistence(timeout: 5))
    attachScreenshot(named: "07-representative-location")
  }

  @MainActor
  private func attachScreenshot(named name: String) {
    let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
    attachment.name = name
    attachment.lifetime = .keepAlways
    add(attachment)
  }
}
