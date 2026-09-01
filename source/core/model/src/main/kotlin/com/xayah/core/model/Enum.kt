package com.xayah.core.model

enum class BackupStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
}

enum class BackupMedium {
    INTERNAL, EXTERNAL, CLOUD
}