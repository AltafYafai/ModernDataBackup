package com.xayah.core.util
import android.util.Log
object LogUtil {
    private const val TAG = "DataBackup"
    fun d(msg: String) = Log.d(TAG, msg)
    fun e(msg: String, t: Throwable? = null) = Log.e(TAG, msg, t)
    fun i(msg: String) = Log.i(TAG, msg)
}
