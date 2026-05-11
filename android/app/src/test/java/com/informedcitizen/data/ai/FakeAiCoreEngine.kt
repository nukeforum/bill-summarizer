package com.informedcitizen.data.ai

import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.UnknownException
import kotlinx.coroutines.CompletableDeferred

/**
 * Test seam for AICore. Tests drive the DownloadCallback synchronously via
 * the emit* methods; prepareInferenceEngine() suspends until the test
 * resolves it by calling completePrepare() or failPrepare().
 */
class FakeAiCoreEngine : AiCoreEngine {
    var callback: DownloadCallback? = null
        private set
    var closed: Boolean = false
        private set
    private val prepareSignal = CompletableDeferred<Unit>()

    fun bind(callback: DownloadCallback) { this.callback = callback }

    override suspend fun prepareInferenceEngine() {
        prepareSignal.await()
    }

    override fun close() { closed = true }

    // Test driver API:
    fun emitDownloadStarted(bytesToDownload: Long) =
        callback!!.onDownloadStarted(bytesToDownload)
    fun emitProgress(totalBytesDownloaded: Long) =
        callback!!.onDownloadProgress(totalBytesDownloaded)
    fun emitCompleted() = callback!!.onDownloadCompleted()
    fun emitFailed(failureStatus: String) =
        callback!!.onDownloadFailed(failureStatus, fakeException())
    fun emitDidNotStart() =
        callback!!.onDownloadDidNotStart(fakeException())
    fun completePrepare() = prepareSignal.complete(Unit)
    fun failPrepare(t: Throwable) = prepareSignal.completeExceptionally(t)

    /**
     * GenerativeAIException is abstract and all its concrete subclasses
     * (UnknownException, DownloadException, ...) have `internal`
     * constructors that the test source set can't see. Build one via
     * reflection so tests can drive the failure callbacks.
     */
    private fun fakeException(): GenerativeAIException {
        val ctor = UnknownException::class.java.declaredConstructors
            .first { it.parameterCount == 3 }
        ctor.isAccessible = true
        return ctor.newInstance("test", null, 0) as GenerativeAIException
    }
}

class FakeAiCoreEngineFactory : AiCoreEngineFactory {
    val engines = mutableListOf<FakeAiCoreEngine>()
    override fun create(callback: DownloadCallback): AiCoreEngine {
        val e = FakeAiCoreEngine().apply { bind(callback) }
        engines.add(e)
        return e
    }
}
