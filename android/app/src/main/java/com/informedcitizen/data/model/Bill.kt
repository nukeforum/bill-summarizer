package com.informedcitizen.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Bill(
    val id: String,
    val congress: Int,
    val type: String,
    val number: String,
    val title: String,
    @SerialName("short_title") val shortTitle: String? = null,
    val sponsor: Sponsor,
    @SerialName("introduced_date") val introducedDate: String,
    @SerialName("latest_action") val latestAction: Action,
    val outcome: Outcome,
    @SerialName("summary_crs") val summaryCrs: String? = null,
    @SerialName("text_url_html") val textUrlHtml: String? = null,
    @SerialName("text_url_xml") val textUrlXml: String? = null,
    @SerialName("text_url_pdf") val textUrlPdf: String? = null,
    @SerialName("congress_gov_url") val congressGovUrl: String,
)
