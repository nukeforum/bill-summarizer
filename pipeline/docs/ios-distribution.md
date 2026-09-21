# iOS distribution

The shared Kotlin Multiplatform pipeline is distributed to the native iOS app as a static XCFramework wrapped by Swift Package Manager.

## Build the framework

Run the release assembly on macOS:

```bash
cd pipeline
./gradlew :ios-bridge:assembleInformedCitizenPipelineReleaseXCFramework
```

The output is written to:

```text
ios-bridge/build/XCFrameworks/release/InformedCitizenPipeline.xcframework
```

It contains these slices:

- `ios-arm64` for physical devices.
- `ios-arm64_x86_64-simulator` for Apple Silicon and Intel simulators.

The framework is static to avoid duplicate Kotlin runtime symbols when the app adds other native dependencies.

`ios-bridge` depends on `shared` as an implementation detail rather than exporting it. Its public API is intentionally limited to Swift-friendly bridge types, keeping Ktor, Okio, kotlinx, and the broader pipeline implementation out of the generated Apple header.

## Swift Package Manager release flow

Generated frameworks are build products and must not be committed to the repository.

For a release:

1. Assemble the release XCFramework from a tagged commit.
2. Archive it as `InformedCitizenPipeline.xcframework.zip` without changing its internal layout.
3. Publish the archive as a GitHub release asset.
4. Calculate its checksum with `swift package compute-checksum`.
5. Update the binary target URL and checksum in the iOS Swift package.

The Swift package must expose a native adapter target above the binary target. SwiftUI features depend only on injected Swift clients and Swift domain models; Kotlin-generated types stay inside the adapter.

## Versioning

The XCFramework and its Swift package wrapper use the repository release tag as their shared version. Any change to the Kotlin/Native public ABI requires a new release artifact and checksum.
