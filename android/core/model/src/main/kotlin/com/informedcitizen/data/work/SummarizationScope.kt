package com.informedcitizen.data.work

sealed interface SummarizationScope {
    data object FloorActionOnly : SummarizationScope
    data object Recent60Days : SummarizationScope
    data class Progressive(val capPerDay: Int) : SummarizationScope {
        init { require(capPerDay in 1..500) { "cap must be 1..500, got $capPerDay" } }
    }
    data object All : SummarizationScope

    companion object {
        val DEFAULT: SummarizationScope = Progressive(capPerDay = 50)
    }
}
