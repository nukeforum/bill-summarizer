import SafariServices
import SwiftUI

struct InAppBrowserModifier: ViewModifier {
  @State private var destination: BrowserDestination?

  func body(content: Content) -> some View {
    content
      .environment(
        \.openURL,
        OpenURLAction { url in
          guard url.isWebDestination else {
            return .systemAction(url)
          }

          destination = BrowserDestination(url: url)
          return .handled
        }
      )
      .sheet(item: $destination) { destination in
        SafariView(url: destination.url)
          .ignoresSafeArea()
      }
  }
}

extension View {
  func presentsWebDestinationsInApp() -> some View {
    modifier(InAppBrowserModifier())
  }
}

private struct BrowserDestination: Identifiable {
  let url: URL

  var id: URL { url }
}

private struct SafariView: UIViewControllerRepresentable {
  let url: URL

  func makeUIViewController(context: Context) -> SFSafariViewController {
    let configuration = SFSafariViewController.Configuration()
    configuration.entersReaderIfAvailable = false
    return SFSafariViewController(url: url, configuration: configuration)
  }

  func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

extension URL {
  fileprivate var isWebDestination: Bool {
    guard let scheme = scheme?.lowercased() else { return false }
    return scheme == "http" || scheme == "https"
  }
}
