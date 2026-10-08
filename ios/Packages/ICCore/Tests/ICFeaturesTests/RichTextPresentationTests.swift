import Foundation
import ICFeatures
import Testing

@Suite("Rich text presentation")
struct RichTextPresentationTests {
  @Test("Renders CRS HTML without exposing tags or entities")
  func rendersHTML() {
    let source = """
      <p><strong>Plain-language overview</strong></p>
      <p>Funding for research &amp; public access.</p>
      <ul><li>First item</li><li>Second item</li></ul>
      """

    let output = RichTextPresentation.attributedString(from: source)
    let text = String(output.characters)

    #expect(text.contains("Plain-language overview"))
    #expect(text.contains("Funding for research & public access."))
    #expect(text.contains("• First item"))
    #expect(text.contains("• Second item"))
    #expect(!text.contains("<p>"))
    #expect(!text.contains("&amp;"))
  }

  @Test("Renders Markdown blocks and falls back safely for plain text")
  func rendersMarkdownAndPlainText() {
    let markdown = RichTextPresentation.attributedString(
      from: """
        ## Important

        Read the [official source](https://www.congress.gov).

        - First item
        - Second item
        """
    )
    let plain = RichTextPresentation.attributedString(from: "No markup here.")

    #expect(
      String(markdown.characters)
        == "Important\n\nRead the official source.\n• First item\n• Second item")
    #expect(String(plain.characters) == "No markup here.")
  }
}
