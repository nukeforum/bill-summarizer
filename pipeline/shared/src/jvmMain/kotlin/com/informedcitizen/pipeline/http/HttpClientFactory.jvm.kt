package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPipelineHttpClient(config: PipelineHttpConfig): HttpClient =
    HttpClient(OkHttp) {
        configurePipeline(config)
    }
