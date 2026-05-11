package com.informedcitizen.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AiCapabilityImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `probe on capable device emits DownloadAvailable without triggering download`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = AiCapabilityImpl(context, factory)

        val first = impl.status.first()
        assertEquals(AiCapability.Status.DownloadAvailable, first)
        assertTrue("Probe must not create an AICore engine", factory.engines.isEmpty())
    }
}
