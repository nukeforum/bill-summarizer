package com.informedcitizen.pipeline

/**
 * Mirrors Python `_common._STATE_NAME_TO_CODE`. Includes the 50 states
 * plus D.C. and the five voting-delegate jurisdictions (AS, GU, MP, PR,
 * VI) that send a single non-voting representative to the House.
 */
private val STATE_NAME_TO_CODE: Map<String, String> = mapOf(
    "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR",
    "California" to "CA", "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE",
    "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID",
    "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS",
    "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD",
    "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS",
    "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV",
    "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY",
    "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK",
    "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC",
    "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT",
    "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA", "West Virginia" to "WV",
    "Wisconsin" to "WI", "Wyoming" to "WY",
    "District of Columbia" to "DC", "American Samoa" to "AS", "Guam" to "GU",
    "Northern Mariana Islands" to "MP", "Puerto Rico" to "PR", "Virgin Islands" to "VI",
)

/**
 * Map a Congress.gov state field to a 2-letter postal code. Two-letter
 * inputs pass through uppercased. Full names are looked up in the table.
 * Unknown names fall back to the first two characters uppercased and
 * are surfaced through [warn] so a future upstream label can be added.
 *
 * Mirrors Python `_common._state_code` with the stderr warning lifted
 * out so commonMain stays I/O-free.
 */
fun stateCode(stateName: String?, warn: (String) -> Unit = {}): String {
    if (stateName.isNullOrEmpty()) return ""
    if (stateName.length == 2) return stateName.uppercase()
    STATE_NAME_TO_CODE[stateName]?.let { return it }
    val fallback = stateName.take(2).uppercase()
    warn("unknown state name '$stateName'; falling back to '$fallback'")
    return fallback
}
