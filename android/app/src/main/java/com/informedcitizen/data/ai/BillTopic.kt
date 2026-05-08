package com.informedcitizen.data.ai

enum class BillTopic(val displayName: String) {
    Tech("Tech"),
    Healthcare("Healthcare"),
    TaxAndBudget("Tax & Budget"),
    Defense("Defense"),
    ForeignAffairs("Foreign Affairs"),
    EnergyAndEnvironment("Energy & Environment"),
    Education("Education"),
    JusticeAndCrime("Justice & Crime"),
    Immigration("Immigration"),
    LaborAndWorkforce("Labor & Workforce"),
    Agriculture("Agriculture"),
    Transportation("Transportation"),
    Housing("Housing"),
    Veterans("Veterans"),
    Trade("Trade"),
    BankingAndFinance("Banking & Finance"),
    CivilRights("Civil Rights"),
    Elections("Elections"),
    ScienceAndSpace("Science & Space"),
    GovernmentOperations("Gov't Operations"),
    Other("Other");

    companion object {
        fun fromName(name: String): BillTopic? =
            values().firstOrNull { it.name == name }
    }
}
