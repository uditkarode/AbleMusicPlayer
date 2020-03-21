/*
    Copyright 2020 yausername  <yauser@protonmail.com>
    Copyright 2020 Udit Karode <udit.karode@gmail.com>

    This file is part of AbleMusicPlayer.

    AbleMusicPlayer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    AbleMusicPlayer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AbleMusicPlayer.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.yausername.youtubedl_android.mapper

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
class VideoInfo {
    var id: String? = null
    var fulltitle: String? = null
    var title: String? = null

    @JsonProperty("upload_date")
    var uploadDate: String? = null

    @JsonProperty("display_id")
    var displayId: String? = null
    var duration = 0
    var description: String? = null
    var thumbnail: String? = null
    var license: String? = null

    //some useful getters
    @JsonProperty("view_count")
    var viewCount: String? = null

    @JsonProperty("like_count")
    var likeCount: String? = null

    @JsonProperty("dislike_count")
    var dislikeCount: String? = null

    @JsonProperty("repost_count")
    var repostCount: String? = null

    @JsonProperty("average_rating")
    var averageRating: String? = null

    @JsonProperty("uploader_id")
    var uploaderId: String? = null
    var uploader: String? = null

    @JsonProperty("player_url")
    var playerUrl: String? = null

    @JsonProperty("webpage_url")
    var webpageUrl: String? = null

    @JsonProperty("webpage_url_basename")
    var webpageUrlBasename: String? = null
    var resolution: String? = null
    var width = 0
    var height = 0
    var format: String? = null
    var ext: String? = null

    @JsonProperty("http_headers")
    var httpHeader: HttpHeader? = null
    var categories: ArrayList<String>? = null
    var tags: ArrayList<String>? = null
    var formats: ArrayList<VideoFormat>? = null
    var thumbnails: ArrayList<VideoThumbnail>? = null

    //public ArrayList<VideoSubtitle> subtitles;
    @JsonProperty("manifest_url")
    var manifestUrl: String? = null

    override fun toString(): String {
        return ("VideoInfo [id=" + id + ", fulltitle=" + fulltitle + ", title=" + title + ", uploadDate=" + uploadDate
                + ", displayId=" + displayId + ", duration=" + duration + ", description=" + description
                + ", thumbnail=" + thumbnail + ", license=" + license + ", viewCount=" + viewCount + ", likeCount="
                + likeCount + ", dislikeCount=" + dislikeCount + ", repostCount=" + repostCount + ", averageRating="
                + averageRating + ", uploaderId=" + uploaderId + ", uploader=" + uploader + ", playerUrl=" + playerUrl
                + ", webpageUrl=" + webpageUrl + ", webpageUrlBasename=" + webpageUrlBasename + ", resolution="
                + resolution + ", width=" + width + ", height=" + height + ", format=" + format + ", ext=" + ext
                + ", httpHeader=" + httpHeader + ", categories=" + categories + ", tags=" + tags + ", formats="
                + formats + ", thumbnails=" + thumbnails + "]")
    }
}