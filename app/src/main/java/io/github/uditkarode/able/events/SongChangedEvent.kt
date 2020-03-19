package io.github.uditkarode.able.events

import io.github.uditkarode.able.models.Song

class SongChangedEvent(val song: Song, val duration: Int)