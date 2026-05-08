package com.informedcitizen.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BillTopicTest {
    @Test
    fun `enum has 21 values`() {
        assertEquals(21, BillTopic.values().size)
    }

    @Test
    fun `display name is not blank for any value`() {
        BillTopic.values().forEach {
            assertNotNull(it.displayName)
            assert(it.displayName.isNotBlank()) { "${it.name} has blank displayName" }
        }
    }

    @Test
    fun `fromName resolves canonical names`() {
        assertEquals(BillTopic.Tech, BillTopic.fromName("Tech"))
        assertEquals(BillTopic.GovernmentOperations, BillTopic.fromName("GovernmentOperations"))
        assertEquals(BillTopic.Other, BillTopic.fromName("Other"))
    }

    @Test
    fun `fromName returns null for unknown name`() {
        assertNull(BillTopic.fromName("NotARealTopic"))
        assertNull(BillTopic.fromName(""))
        assertNull(BillTopic.fromName("tech"))
    }
}
