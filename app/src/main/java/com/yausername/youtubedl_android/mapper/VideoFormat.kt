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

@JsonIgnoreProperties(ignoreUnknown = true)
class VideoFormat {
    var asr = 0
    var tbr = 0
    var abr = 0
    var format: String? = null

    @JsonProperty("format_id")
    var formatId: String? = null

    @JsonProperty("format_note")
    var formatNote: String? = null
    var ext: String? = null
    var preference = 0
    var vcodec: String? = null
    var acodec: String? = null
    var width = 0
    var height = 0
    var filesize: Long = 0
    var fps = 0
    var url: String? = null

    @JsonProperty("manifest_url")
    var manifestUrl: String? = null
}