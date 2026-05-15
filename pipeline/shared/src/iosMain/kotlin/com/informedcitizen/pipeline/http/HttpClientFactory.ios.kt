package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createPipelineHttpClient(config: PipelineHttpConfig): HttpClient =
    HttpClient(Darwin) {
        configurePipeline(config)
    }
