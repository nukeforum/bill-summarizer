package com.informedcitizen.pipeline.cli

/**
 * Pipeline CLI entrypoint. Subcommands ported so far:
 *  - `fetch-bills` — daily refresh of the per-Congress bills manifest.
 *
 * Subcommands still pending port (Python scripts continue to handle
 * them in CI until they land): `fetch-members`, `backfill-bills`,
 * `build-session-calendar`, `build-zip-crosswalk`, `check-freshness`,
 * `rebuild-congresses-index`. See TODO "Shared Pipeline (KMP)".
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }
    val exitCode = when (val cmd = args[0]) {
        "fetch-bills" -> FetchBillsCommand.run(args.drop(1))
        "--help", "-h", "help" -> {
            printUsage()
            0
        }
        else -> {
            System.err.println("Unknown subcommand: $cmd")
            printUsage()
            2
        }
    }
    if (exitCode != 0) {
        kotlin.system.exitProcess(exitCode)
    }
}

private fun printUsage() {
    println(
        """
        pipeline-cli — bill-summarizer data pipeline (Kotlin port)

        Usage:
          pipeline-cli <subcommand> [options]

        Subcommands:
          fetch-bills [--output-dir <path>]
              Daily refresh of the per-Congress bills manifest. Reads
              CONGRESS_API_KEY from the environment. Writes
              <output-dir>/congressNNN_bills.json (default: ./docs/data).
              Does NOT rebuild congresses.json; run the Python
              build_dashboard.py / rebuild_index step after this for
              the indexed view.
          help
              Show this message.
        """.trimIndent(),
    )
}
