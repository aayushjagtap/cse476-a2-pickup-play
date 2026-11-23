package com.aayushjagtap.pickupplay.data

/**
 * A value delivered through LiveData that must be handled only once (for example an error
 * Snackbar), so it is not shown again after rotation re-delivers the latest LiveData value.
 */
class OneShot<out T>(private val content: T) {
    private var handled = false

    /** Returns the content the first time, then null. */
    fun take(): T? = if (handled) null else content.also { handled = true }
}
