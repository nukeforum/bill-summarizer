package com.informedcitizen.pipeline

data class ErrorRecord(
    val kind: String,
    val identifier: String,
    val errorClass: String,
    val message: String,
    val url: String? = null,
    val params: Map<String, String>? = null,
)

class ErrorCollector {
    private val errors = mutableListOf<ErrorRecord>()

    val size: Int get() = errors.size
    val hasErrors: Boolean get() = errors.isNotEmpty()

    fun records(): List<ErrorRecord> = errors.toList()

    fun record(
        kind: String,
        identifier: String,
        errorClass: String,
        message: String,
        url: String? = null,
        params: Map<String, String>? = null,
    ) {
        errors += ErrorRecord(kind, identifier, errorClass, message, url, params)
    }

    fun summaryLines(examplesPerClass: Int = 5): List<String> {
        if (errors.isEmpty()) return emptyList()
        val groups = LinkedHashMap<Pair<String, String>, MutableList<ErrorRecord>>()
        for (rec in errors) {
            groups.getOrPut(rec.kind to rec.errorClass) { mutableListOf() }.add(rec)
        }
        val lines = mutableListOf<String>()
        lines += "${errors.size} error(s) during run:"
        for ((key, recs) in groups) {
            val (kind, errorClass) = key
            lines += "  $kind / $errorClass × ${recs.size}"
            for (rec in recs.take(examplesPerClass)) {
                val detail = buildString {
                    append("    - ").append(rec.identifier).append(": ").append(rec.message)
                    rec.url?.let { append(" [url=").append(it).append("]") }
                    rec.params?.let { append(" [params=").append(it).append("]") }
                }
                lines += detail
            }
            val remaining = recs.size - examplesPerClass
            if (remaining > 0) lines += "    … $remaining more"
        }
        return lines
    }

    fun renderSummary(label: String? = null, examplesPerClass: Int = 5): String {
        if (errors.isEmpty()) return ""
        val header = label?.let { "--- $it errors ---\n" }.orEmpty()
        return header + summaryLines(examplesPerClass).joinToString("\n")
    }
}
