package com.xayah.modernnative
object ZstdWrapper {
    init { System.loadLibrary("modernnative") }
    external fun stringFromJNI(): String
    external fun compress(src: ByteArray): Int
}
