package com.informedcitizen.pipeline.fetch

/** Mirrors Python `fetch_votes.votes_index_path`. */
fun votesIndexFileName(congress: Int): String = "congress${congress}_votes.json"
