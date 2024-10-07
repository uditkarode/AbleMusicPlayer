package io.github.uditkarode.able.model.spotifyplaylist

import com.google.gson.annotations.SerializedName

data class AddedBy (
    @SerializedName("external_urls") val externalURL : ExternalURL,
    @SerializedName("href") val href : String,
    @SerializedName("id") val id : String,
    @SerializedName("type") val type : String,
    @SerializedName("uri") val uri : String
)