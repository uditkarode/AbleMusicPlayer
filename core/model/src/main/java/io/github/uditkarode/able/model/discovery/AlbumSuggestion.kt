package io.github.uditkarode.able.model.discovery

/**
 * A Last.fm album in an artist's discography, used by the drill-down view in
 * the Search tab (albums newest→oldest, then a "Singles" section).
 */
class AlbumSuggestion(
    /** Album title, e.g. "The Dark Side of the Moon" */
    val name: String,
    /** Album artist */
    val artist: String,
    /** Last.fm artwork URL (may be empty when the catalog has no art) */
    var image: String = "",
    /** Raw release date as reported by Last.fm (format varies), may be empty */
    val releaseDate: String = "",
    /** Release year, extracted from [releaseDate] when parseable (0 = unknown) */
    val year: Int = 0,
    /** Full track list from album.getInfo */
    var tracks: List<TrackSuggestion> = emptyList()
)
