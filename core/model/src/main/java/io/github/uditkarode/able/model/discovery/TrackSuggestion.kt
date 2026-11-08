package io.github.uditkarode.able.model.discovery

/**
 * A track discovered through Last.fm. Suggestions come straight from the Last.fm
 * catalog and therefore have no playable source yet — `youtubeLink` is resolved
 * lazily on YouTube Music when the user presses the item (see [io.github.uditkarode.able.utils.TrackResolver]).
 */
class TrackSuggestion(
    /** Track title, e.g. "Bohemian Rhapsody" */
    val name: String,
    /** Artist name, e.g. "Queen" */
    var artist: String = "",
    /** Last.fm artwork URL (may be empty when the catalog has no art) */
    var image: String = "",
    /** Last.fm listeners count, as reported by the API (already formatted string) */
    var listeners: String = "",
    /** MusicBrainz ID, can be empty */
    val mbid: String = "",
    /** YouTube Music watch link, filled on first press */
    var youtubeLink: String = "",
    /** YouTube thumbnail, filled together with [youtubeLink] */
    var youtubeThumbnail: String = ""
)

/**
 * An artist discovered through Last.fm (chart/geo top artists). Pressing an artist
 * drill-downs into its top tracks.
 */
class ArtistSuggestion(
    /** Artist name, e.g. "Daft Punk" */
    val name: String,
    /** Last.fm artwork URL (may be empty when the catalog has no art) */
    var image: String = "",
    /** Last.fm listeners count */
    var listeners: String = "",
    /** MusicBrainz ID, can be empty */
    val mbid: String = ""
)
