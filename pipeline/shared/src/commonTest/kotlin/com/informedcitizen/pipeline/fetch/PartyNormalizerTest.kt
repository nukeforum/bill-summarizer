package com.informedcitizen.pipeline.fetch

import kotlin.test.Test
import kotlin.test.assertEquals

class PartyNormalizerTest {
    @Test fun empty_input_returns_empty() = assertEquals("", normalizePartyCode(""))
    @Test fun null_input_returns_empty() = assertEquals("", normalizePartyCode(null))
    @Test fun whitespace_only_returns_empty() = assertEquals("", normalizePartyCode("   "))
    @Test fun democratic_normalizes_to_D() = assertEquals("D", normalizePartyCode("Democratic"))
    @Test fun democrat_normalizes_to_D() = assertEquals("D", normalizePartyCode("Democrat"))
    @Test fun republican_normalizes_to_R() = assertEquals("R", normalizePartyCode("Republican"))
    @Test fun independent_normalizes_to_I() = assertEquals("I", normalizePartyCode("Independent"))
    @Test fun lowercase_d_normalizes_to_D() = assertEquals("D", normalizePartyCode("democrat"))
    @Test fun surrounding_whitespace_is_trimmed() = assertEquals("R", normalizePartyCode("  Republican  "))
    @Test fun other_party_takes_first_char_uppercased() = assertEquals("L", normalizePartyCode("Libertarian"))
}
