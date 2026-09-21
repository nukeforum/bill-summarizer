// swift-tools-version: 6.2

import PackageDescription

let package = Package(
  name: "ICCore",
  platforms: [
    .iOS(.v17),
    .macOS(.v14),
  ],
  products: [
    .library(name: "ICModels", targets: ["ICModels"]),
    .library(name: "ICClients", targets: ["ICClients"]),
    .library(name: "ICDataKit", targets: ["ICDataKit"]),
    .library(name: "ICDesign", targets: ["ICDesign"]),
    .library(name: "ICFeatures", targets: ["ICFeatures"]),
  ],
  dependencies: [
    .package(
      url: "https://github.com/pointfreeco/swift-dependencies",
      from: "1.17.1"
    )
  ],
  targets: [
    .target(name: "ICModels"),
    .target(
      name: "ICClients",
      dependencies: [
        "ICModels",
        .product(name: "Dependencies", package: "swift-dependencies"),
      ]
    ),
    .target(
      name: "ICDataKit",
      dependencies: ["ICModels", "ICClients"]
    ),
    .target(
      name: "ICDesign",
      dependencies: ["ICModels"]
    ),
    .target(
      name: "ICFeatures",
      dependencies: [
        "ICModels",
        "ICClients",
        .product(name: "Dependencies", package: "swift-dependencies"),
      ]
    ),
    .testTarget(
      name: "ICModelsTests",
      dependencies: ["ICModels"]
    ),
    .testTarget(
      name: "ICDataKitTests",
      dependencies: ["ICModels", "ICClients", "ICDataKit"],
      resources: [.process("Fixtures")]
    ),
    .testTarget(
      name: "ICFeaturesTests",
      dependencies: [
        "ICModels",
        "ICClients",
        "ICFeatures",
        .product(name: "Dependencies", package: "swift-dependencies"),
      ]
    ),
  ]
)
