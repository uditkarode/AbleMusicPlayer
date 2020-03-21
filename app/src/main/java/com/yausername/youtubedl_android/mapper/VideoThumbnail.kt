package com.yausername.youtubedl_android.mapper

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class VideoThumbnail {
    var url: String? = null
    var id: String? = null
}