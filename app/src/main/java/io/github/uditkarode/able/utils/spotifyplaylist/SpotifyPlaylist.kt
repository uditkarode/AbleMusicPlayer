package io.github.uditkarode.able.utils.spotifyplaylist

import com.google.gson.annotations.SerializedName

/*
Copyright (c) 2020 Kotlin Data Classes Generated from JSON powered by http://www.json2kotlin.com

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

For support, please feel free to contact me at https://www.linkedin.com/in/syedabsar */


data class SpotifyPlaylist (

	@SerializedName("collaborative") val collaborative : Boolean,
	@SerializedName("description") val description : String,
	@SerializedName("external_urls") val external_urls : External_urls,
	@SerializedName("followers") val followers : Followers,
	@SerializedName("href") val href : String,
	@SerializedName("id") val id : String,
	@SerializedName("images") val images : List<Images>,
	@SerializedName("name") val name : String,
	@SerializedName("owner") val owner : Owner,
	@SerializedName("primary_color") val primary_color : String,
	@SerializedName("public") val public : Boolean,
	@SerializedName("sharing_info") val sharing_info : Sharing_info,
	@SerializedName("snapshot_id") val snapshot_id : String,
	@SerializedName("tracks") val tracks : Tracks,
	@SerializedName("type") val type : String,
	@SerializedName("uri") val uri : String
)