import com.google.gson.annotations.SerializedName
import io.github.uditkarode.able.model.spotifyplaylist.Artists
import io.github.uditkarode.able.model.spotifyplaylist.ExternalURL
import io.github.uditkarode.able.model.spotifyplaylist.Images

data class Album (
	@SerializedName("album_type") val album_type : String,
	@SerializedName("artists") val artists : List<Artists>,
	@SerializedName("available_markets") val available_markets : List<String>,
	@SerializedName("external_urls") val external_URL : ExternalURL,
	@SerializedName("href") val href : String,
	@SerializedName("id") val id : String,
	@SerializedName("images") val images : List<Images>,
	@SerializedName("name") val name : String,
	@SerializedName("release_date") val release_date : String,
	@SerializedName("release_date_precision") val release_date_precision : String,
	@SerializedName("total_tracks") val total_tracks : Int,
	@SerializedName("type") val type : String,
	@SerializedName("uri") val uri : String
)