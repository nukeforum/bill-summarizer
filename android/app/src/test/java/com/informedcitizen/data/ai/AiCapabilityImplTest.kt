package com.informedcitizen.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
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
        val impl = makeImpl(factory, this)

        val first = impl.status.first()
        assertEquals(AiCapability.Status.DownloadAvailable, first)
        assertTrue("Probe must not create an AICore engine", factory.engines.isEmpty())
    }

    @Test fun `requestDownload from DownloadAvailable starts engine and transitions to ModelDownloading`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = makeImpl(factory, this)

        impl.requestDownload()

        assertEquals(1, factory.engines.size)
        val engine = factory.engines.first()
        engine.emitDownloadStarted(bytesToDownload = 1_000_000_000L)
        val s = impl.status.first()
        assertTrue("Expected ModelDownloading, got $s", s is AiCapability.Status.ModelDownloading)
        // Let the runDownload coroutine complete so runTest doesn't hang.
        engine.failPrepare(RuntimeException("end of test"))
    }

    @Test fun `progress fraction reflects downloaded over total`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = makeImpl(factory, this)

        impl.requestDownload()
        val engine = factory.engines.first()
        engine.emitDownloadStarted(bytesToDownload = 1_000L)
        engine.emitProgress(totalBytesDownloaded = 250L)

        val s = impl.status.first() as AiCapability.Status.ModelDownloading
        assertEquals(0.25f, s.progress, 0.001f)
        engine.failPrepare(RuntimeException("end of test"))
    }

    @Test fun `onDownloadFailed transitions to DownloadFailed with reason`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = makeImpl(factory, this)

        impl.requestDownload()
        val engine = factory.engines.first()
        engine.emitDownloadStarted(bytesToDownload = 1_000L)
        engine.emitFailed("NETWORK_ERROR")
        engine.failPrepare(RuntimeException("download failed"))

        val s = impl.status.first()
        assertTrue("Expected DownloadFailed, got $s", s is AiCapability.Status.DownloadFailed)
        assertEquals("NETWORK_ERROR", (s as AiCapability.Status.DownloadFailed).reason)
    }

    @Test fun `onDownloadDidNotStart transitions to NotSupported`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = makeImpl(factory, this)

        impl.requestDownload()
        val engine = factory.engines.first()
        engine.emitDidNotStart()
        engine.failPrepare(RuntimeException("did not start"))

        assertEquals(AiCapability.Status.NotSupported, impl.status.first())
    }

    @Test fun `requestDownload during in-flight download does not start a second engine`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = makeImpl(factory, this)

        impl.requestDownload()
        factory.engines.first().emitDownloadStarted(bytesToDownload = 1_000L)

        impl.requestDownload()

        assertEquals(1, factory.engines.size)
        factory.engines.first().failPrepare(RuntimeException("end of test"))
    }

    @Test fun `requestDownload after DownloadFailed creates a fresh engine`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = makeImpl(factory, this)

        impl.requestDownload()
        val first = factory.engines.first()
        first.emitDownloadStarted(bytesToDownload = 1_000L)
        first.emitFailed("NETWORK_ERROR")
        first.failPrepare(RuntimeException("download failed"))

        // State is now DownloadFailed; retry.
        impl.requestDownload()

        assertEquals(2, factory.engines.size)
        factory.engines[1].failPrepare(RuntimeException("end of test"))
    }

    @Test fun `requestDownload while NotSupported does not create an engine`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = makeImpl(factory, this)

        impl.requestDownload()
        factory.engines.first().emitDidNotStart()
        factory.engines.first().failPrepare(RuntimeException("did not start"))
        assertEquals(AiCapability.Status.NotSupported, impl.status.first())

        impl.requestDownload()

        assertEquals(1, factory.engines.size)  // no new engine
    }

    @Test fun `clean completion transitions to Available`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = AiCapabilityImpl(
            context = ApplicationProvider.getApplicationContext<Context>(),
            engineFactory = factory,
            scope = this,
        )

        impl.requestDownload()
        val engine = factory.engines.first()
        engine.emitDownloadStarted(bytesToDownload = 1_000L)
        engine.emitProgress(totalBytesDownloaded = 1_000L)
        engine.emitCompleted()
        engine.completePrepare()

        assertEquals(AiCapability.Status.Available, impl.status.first())
    }

    @Test fun `clean prepare return after onDownloadFailed preserves DownloadFailed state`() = runTest(UnconfinedTestDispatcher()) {
        val factory = FakeAiCoreEngineFactory()
        val impl = AiCapabilityImpl(
            context = ApplicationProvider.getApplicationContext<Context>(),
            engineFactory = factory,
            scope = this,
        )

        impl.requestDownload()
        val engine = factory.engines.first()
        engine.emitDownloadStarted(bytesToDownload = 1_000L)
        engine.emitFailed("NETWORK_ERROR")
        engine.completePrepare()      // simulate AICore returning normally despite the failure callback

        val s = impl.status.first()
        assertTrue("Expected DownloadFailed, got $s", s is AiCapability.Status.DownloadFailed)
        assertEquals("NETWORK_ERROR", (s as AiCapability.Status.DownloadFailed).reason)
    }

    private fun makeImpl(factory: FakeAiCoreEngineFactory, scope: CoroutineScope): AiCapabilityImpl =
        AiCapabilityImpl(
            context = context,
            engineFactory = factory,
            scope = scope,
        )
}
