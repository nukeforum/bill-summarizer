import Foundation
import ICDataKit
import Testing

@Suite("Published bills API")
struct PublishedBillsAPITests {
  @Test("HTTP errors retain the response status")
  func errorContainsStatus() {
    #expect(PublishedBillsAPIError.httpStatus(503) == .httpStatus(503))
  }
}
