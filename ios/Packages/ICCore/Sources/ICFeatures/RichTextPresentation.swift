import Foundation

public enum RichTextPresentation {
  public static func attributedString(from source: String) -> AttributedString {
    let markdown = normalizeBlockMarkdown(
      source.containsHTML ? markdown(fromHTML: source) : source
    )

    return
      (try? AttributedString(
        markdown: markdown,
        options: .init(
          interpretedSyntax: .inlineOnlyPreservingWhitespace,
          failurePolicy: .returnPartiallyParsedIfPossible
        )
      )) ?? AttributedString(markdown)
  }

  private static func markdown(fromHTML source: String) -> String {
    var output = source
    output = output.replacingPattern(
      #"(?is)<(?:script|style)[^>]*>.*?</(?:script|style)>"#,
      with: ""
    )
    output = output.replacingPattern(
      #"(?is)<a\s+[^>]*href\s*=\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>"#,
      with: "[$2]($1)"
    )

    let replacements = [
      (#"(?i)<h[1-6][^>]*>"#, "**"),
      (#"(?i)</h[1-6]>"#, "**\n\n"),
      (#"(?i)<(?:strong|b)[^>]*>"#, "**"),
      (#"(?i)</(?:strong|b)>"#, "**"),
      (#"(?i)<(?:em|i)[^>]*>"#, "_"),
      (#"(?i)</(?:em|i)>"#, "_"),
      (#"(?i)<li[^>]*>"#, "\n• "),
      (#"(?i)</li>"#, ""),
      (#"(?i)<br\s*/?>"#, "\n"),
      (#"(?i)</p\s*>"#, "\n\n"),
      (#"(?i)</div\s*>"#, "\n"),
      (#"(?i)</(?:ul|ol)\s*>"#, "\n"),
      (#"(?is)<[^>]+>"#, ""),
    ]
    for (pattern, replacement) in replacements {
      output = output.replacingPattern(pattern, with: replacement)
    }

    output = decodeHTMLEntities(in: output)
      .replacingOccurrences(of: "\u{00A0}", with: " ")
    output = output.replacingPattern(#"[ \t]+\n"#, with: "\n")
    output = output.replacingPattern(#"\n{3,}"#, with: "\n\n")
    return output.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private static func normalizeBlockMarkdown(_ source: String) -> String {
    source
      .replacingPattern(#"(?m)^\s*#{1,6}\s+(.+)$"#, with: "**$1**")
      .replacingPattern(#"(?m)^\s*[-+*]\s+"#, with: "• ")
      .replacingPattern(#"(?m)^\s*>\s?"#, with: "▌ ")
  }

  private static func decodeHTMLEntities(in source: String) -> String {
    let prepared = source.replacingOccurrences(of: "\n", with: "<br>")
    guard let data = prepared.data(using: .utf8) else { return source }
    let decoded = try? NSAttributedString(
      data: data,
      options: [
        .documentType: NSAttributedString.DocumentType.html,
        .characterEncoding: String.Encoding.utf8.rawValue,
      ],
      documentAttributes: nil
    )
    return decoded?.string ?? source
  }
}

extension String {
  fileprivate var containsHTML: Bool {
    range(
      of: #"<[a-zA-Z][^>]*>"#,
      options: .regularExpression
    ) != nil
  }

  fileprivate func replacingPattern(_ pattern: String, with replacement: String) -> String {
    replacingOccurrences(
      of: pattern,
      with: replacement,
      options: .regularExpression
    )
  }
}
