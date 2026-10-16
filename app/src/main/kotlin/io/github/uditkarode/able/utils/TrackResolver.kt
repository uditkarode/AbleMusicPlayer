/*
    Copyright 2025 AbleMusicPlayer contributors

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

package io.github.uditkarode.able.utils

import android.util.Log
import io.github.uditkarode.able.model.discovery.TrackSuggestion
import io.github.uditkarode.able.model.song.Song
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Collections.singletonList

/**
 * Turns a Last.fm [TrackSuggestion] into a playable [Song] by searching for
 * "artist - title" on YouTube Music. The resolution happens lazily, only when the
 * user presses a suggestion, and the result is memoized on the suggestion itself.
 */
object TrackResolver {

    /**
     * @return the resolved [Song], or null when YouTube Music has no match.
     * Blocking — call from a background dispatcher.
     */
    fun resolve(suggestion: TrackSuggestion): Song? {
        if (suggestion.youtubeLink.isNotBlank())
            return suggestion.asSong()

        val query = if (suggestion.artist.isBlank()) suggestion.name
                    else "${suggestion.artist} ${suggestion.name}"

        return try {
            val extractor = YouTube.getSearchExtractor(
                query, singletonList(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), ""
            )
            extractor.fetchPage()

            val best = extractor.initialPage.items.firstOrNull() as? StreamInfoItem
                ?: return null

            Log.i("ABLE>", "trackResolver '${query}' → ${best.url}")
            suggestion.youtubeLink = best.url
            suggestion.youtubeThumbnail = Shared.getBestThumbnail(best.thumbnails, best.url)
            suggestion.asSong()
        } catch (e: Exception) {
            null
        }
    }

    private fun TrackSuggestion.asSong() = Song(
        name = name,
        artist = artist,
        youtubeLink = youtubeLink,
        ytmThumbnail = youtubeThumbnail
    )
}
