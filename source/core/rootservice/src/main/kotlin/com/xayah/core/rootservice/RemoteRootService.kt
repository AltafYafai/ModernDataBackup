package com.xayah.core.rootservice
data class CommandResult(val code: Int, val out: String, val err: List<String>)
interface RootListener { fun onRootAvailable() }
interface RemoteRootService {
    fun isRootAvailable(): Boolean
    fun execute(command: String): CommandResult
    fun addRootListener(listener: RootListener)
    fun removeRootListener(listener: RootListener)
}
