package com.xayah.core.rootservice

import com.github.topjohnwu.libsu.core.api.RootShell
import com.github.topjohnwu.libsu.core.command.CommandResult
import com.github.topjohnwu.libsu.core.listener.RootListener

interface RemoteRootService {
    fun isRootAvailable(): Boolean
    fun execute(command: String): CommandResult
    fun addRootListener(listener: RootListener)
    fun removeRootListener(listener: RootListener)
}