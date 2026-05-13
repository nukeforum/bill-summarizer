package com.informedcitizen.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSummaryParserTest {

    @Test fun `valid json parses to success`() {
        val raw = """{"concise_title":"Speed up rural broadband","topic":"Tech"}"""
        val result = BillSummaryParser.parse(raw)
        assertTrue(result is BillSummaryParser.Result.Success)
        result as BillSummaryParser.Result.Success
        assertEquals("Speed up rural broadband", result.summary.generatedTitle)
        assertEquals(BillTopic.Tech, result.summary.topic)
    }

    @Test fun `unknown topic coerces to Other`() {
        val raw = """{"concise_title":"Some bill","topic":"NotARealTopic"}"""
        val result = BillSummaryParser.parse(raw)
        assertTrue(result is BillSummaryParser.Result.Success)
        assertEquals(
            BillTopic.Other,
            (result as BillSummaryParser.Result.Success).summary.topic,
        )
    }

    @Test fun `malformed json returns ParseFailed`() {
        val result = BillSummaryParser.parse("this is not json")
        assertEquals(BillSummaryParser.Result.ParseFailed, result)
    }

    @Test fun `empty title returns ParseFailed`() {
        val raw = """{"concise_title":"","topic":"Tech"}"""
        assertEquals(BillSummaryParser.Result.ParseFailed, BillSummaryParser.parse(raw))
    }

    @Test fun `oversize title (over 80 chars) returns ParseFailed`() {
        val long = "x".repeat(81)
        val raw = """{"concise_title":"$long","topic":"Tech"}"""
        assertEquals(BillSummaryParser.Result.ParseFailed, BillSummaryParser.parse(raw))
    }

    @Test fun `missing fields returns ParseFailed`() {
        assertEquals(
            BillSummaryParser.Result.ParseFailed,
            BillSummaryParser.parse("""{"concise_title":"hi"}"""),
        )
        assertEquals(
            BillSummaryParser.Result.ParseFailed,
            BillSummaryParser.parse("""{"topic":"Tech"}"""),
        )
    }

    @Test fun `whitespace-trimmed title at boundary passes`() {
        val raw = """{"concise_title":"  Trimmed title  ","topic":"Tech"}"""
        val result = BillSummaryParser.parse(raw)
        assertTrue(result is BillSummaryParser.Result.Success)
        assertEquals(
            "Trimmed title",
            (result as BillSummaryParser.Result.Success).summary.generatedTitle,
        )
    }
}
