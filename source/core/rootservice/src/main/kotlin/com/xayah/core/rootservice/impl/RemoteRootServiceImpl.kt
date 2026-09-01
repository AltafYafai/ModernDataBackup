package com.xayah.core.rootservice.impl
import com.xayah.core.rootservice.CommandResult
import com.xayah.core.rootservice.RemoteRootService
import com.xayah.core.rootservice.RootListener
class RemoteRootServiceImpl : RemoteRootService {
    override fun isRootAvailable(): Boolean = true
    override fun execute(command: String): CommandResult = CommandResult(0, "", emptyList())
    override fun addRootListener(listener: RootListener) {}
    override fun removeRootListener(listener: RootListener) {}
}
