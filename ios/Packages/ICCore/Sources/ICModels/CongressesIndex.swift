public struct CongressesIndex: Codable, Equatable, Sendable {
  public let generatedAt: String
  public let currentCongress: Int
  public let congresses: [CongressEntry]

  private enum CodingKeys: String, CodingKey {
    case generatedAt = "generated_at"
    case currentCongress = "current_congress"
    case congresses
  }
}

public struct CongressEntry: Codable, Equatable, Sendable {
  public let congress: Int
  public let billCount: Int
  public let manifestPath: String
  public let shardIndexPath: String?

  private enum CodingKeys: String, CodingKey {
    case congress
    case billCount = "bill_count"
    case manifestPath = "manifest_path"
    case shardIndexPath = "shard_index_path"
  }
}
