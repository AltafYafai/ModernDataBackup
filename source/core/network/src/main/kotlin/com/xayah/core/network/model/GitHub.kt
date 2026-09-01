package com.xayah.core.network.model
data class GitHubRelease(val tagName: String, val assets: List<Asset> = emptyList()) {
    data class Asset(val name: String, val url: String, val size: Long = 0)
}
