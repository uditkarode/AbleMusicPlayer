package io.github.uditkarode.able.events

/**
 * Used to notify the UI when the song name/artist name changes.
 * (It can be manually changed by the user from the Player)
 */
class GetMetaDataEvent(val name: String? = null, val artist: String? = null)