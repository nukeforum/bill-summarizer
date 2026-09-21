# Informed Citizen for iOS

The iOS port is a native SwiftUI application. Android remains the product-behavior reference, while the shared pipeline owns the published wire contract.

## Architecture

- Swift 6 with complete concurrency checking.
- iOS 17 minimum deployment target.
- Point-Free Dependencies for every external effect.
- No global service singletons.
- Local Swift package boundaries under `Packages/ICCore`.
- KMP access will be isolated behind a future `PipelineClient`; Kotlin types must not enter feature or view code.
- Deterministic `-SCREENSHOT_MODE` launch behavior for UI validation.

## Local validation

```bash
cd ios/Packages/ICCore
swift test

xcodebuild \
  -project ios/InformedCitizen.xcodeproj \
  -scheme InformedCitizen \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  test
```

The Xcode project is committed. Add application and UI-test sources to their existing groups and targets in Xcode; package sources are discovered automatically by SwiftPM.
