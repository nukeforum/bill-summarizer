package com.informedcitizen.pipeline

const val OUTCOME_PASSED_HOUSE: String = "passed_house"
const val OUTCOME_PASSED_SENATE: String = "passed_senate"
const val OUTCOME_ENACTED: String = "enacted"
const val OUTCOME_VETOED: String = "vetoed"
const val OUTCOME_FAILED: String = "failed"

private val OUTCOME_RULES: List<Pair<String, List<String>>> = listOf(
    OUTCOME_ENACTED to listOf("became public law", "became law"),
    OUTCOME_VETOED to listOf("vetoed by president"),
    OUTCOME_FAILED to listOf(
        "failed of passage",
        "motion to table agreed to",
        "failed to pass",
        "rejected",
    ),
    OUTCOME_PASSED_HOUSE to listOf(
        "passed/agreed to in house",
        "passed house",
        "on passage passed by the house",
        "agreed to in house",
    ),
    OUTCOME_PASSED_SENATE to listOf(
        "passed/agreed to in senate",
        "passed senate",
        "on passage passed by the senate",
        "agreed to in senate",
    ),
)

fun classifyOutcome(actionText: String): String? {
    val needle = actionText.lowercase()
    for ((outcome, patterns) in OUTCOME_RULES) {
        if (patterns.any { it in needle }) return outcome
    }
    return null
}
