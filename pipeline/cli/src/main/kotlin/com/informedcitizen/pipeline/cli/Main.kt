package com.informedcitizen.pipeline.cli

import com.informedcitizen.pipeline.cleanSponsorName
import com.informedcitizen.pipeline.classifyOutcome
import com.informedcitizen.pipeline.congressForYear

/**
 * Placeholder CLI entrypoint. Subcommands (`fetch-bills`, `fetch-members`,
 * `backfill-bills`, `build-session-calendar`, `build-zip-crosswalk`,
 * `check-freshness`) will be wired in as the corresponding Python scripts
 * are ported into the shared module. See TODO "Shared Pipeline (KMP)".
 */
fun main(args: Array<String>) {
    println("pipeline-cli (placeholder)")
    println("  current congress: ${congressForYear(2026)}")
    println("  classifyOutcome('Became Public Law No: 119-12.') = ${classifyOutcome("Became Public Law No: 119-12.")}")
    println("  cleanSponsorName('Rep. Smith, Adrian [R-NE-3]') = '${cleanSponsorName("Rep. Smith, Adrian [R-NE-3]")}'")
    if (args.isNotEmpty()) println("  args: ${args.joinToString(" ")}")
}
