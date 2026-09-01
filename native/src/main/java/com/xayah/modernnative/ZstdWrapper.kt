package com.xayah.modernnative
object ZstdWrapper {
    private var isLoaded: Boolean = false
    init {
        try {
            System.loadLibrary("modernnative")
            isLoaded = true
        } catch (t: Throwable) {
            isLoaded = false
        }
    }
    fun isAvailable(): Boolean = isLoaded
    external fun stringFromJNI(): String
    external fun compress(src: ByteArray): Int
}
