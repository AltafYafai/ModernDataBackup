package com.xayah.core.rootservice.impl

import com.github.topjohnwu.libsu.core.command.CommandResult
import com.github.topjohnwu.libsu.core.listener.RootListener
import com.xayah.core.rootservice.RemoteRootService

class RemoteRootServiceImpl : RemoteRootService {
    override fun isRootAvailable(): Boolean = true

    override fun execute(command: String): CommandResult = CommandResult(0, "", emptyList())

    override fun addRootListener(listener: RootListener) {}

    override fun removeRootListener(listener: RootListener) {}
}