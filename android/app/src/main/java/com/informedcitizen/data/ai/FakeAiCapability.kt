package com.informedcitizen.data.ai

import kotlinx.coroutines.flow.MutableStateFlow

class FakeAiCapability(
    initial: AiCapability.Status = AiCapability.Status.Available,
) : AiCapability {
    private val state = MutableStateFlow(initial)
    override val status = state
    fun set(status: AiCapability.Status) { state.value = status }
}
