package com.gemmathon

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object DebugLog {
    private const val TAG = "Gemmathon"
    private val entries = CopyOnWriteArrayList<String>()
    private val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun d(message: String) {
        val ts = sdf.format(Date())
        entries.add("$ts D $TAG: $message")
        Log.d(TAG, message)
    }

    fun getAll(): String = entries.joinToString("\n")

    fun clear() = entries.clear()
}
