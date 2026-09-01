package com.xayah.core.network.model
import kotlinx.serialization.Serializable
@Serializable
data class GitHubRelease(val tagName: String, val assets: List<Asset> = emptyList()) {
    @Serializable data class Asset(val name: String, val url: String, val size: Long)
}
