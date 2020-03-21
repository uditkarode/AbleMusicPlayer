package com.yausername.youtubedl_android.mapper

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class HttpHeader {
    @JsonProperty("Accept-Charset")
    var acceptCharset: String? = null

    @JsonProperty("Accept-Language")
    var acceptLanguage: String? = null

    @JsonProperty("Accept-Encoding")
    var acceptEncoding: String? = null

    @JsonProperty("Accept")
    var accept: String? = null

    @JsonProperty("User-Agent")
    var userAgent: String? = null
}