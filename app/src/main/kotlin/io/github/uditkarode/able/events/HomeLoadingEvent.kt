package io.github.uditkarode.able.events

/**
 * Notifies the UI if streaming has started/finished so that progress bars can be stopped.
 */
class HomeLoadingEvent(val loading: Boolean)