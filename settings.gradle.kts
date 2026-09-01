pluginManagement {
    includeBuild("build-logic/convention")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "ModernDataBackup"
include(":app")
include(":core:common"); project(":core:common").projectDir = file("source/core/common")
include(":core:model"); project(":core:model").projectDir = file("source/core/model")
include(":core:database"); project(":core:database").projectDir = file("source/core/database")
include(":core:data"); project(":core:data").projectDir = file("source/core/data")
include(":core:datastore"); project(":core:datastore").projectDir = file("source/core/datastore")
include(":core:network"); project(":core:network").projectDir = file("source/core/network")
include(":core:ui"); project(":core:ui").projectDir = file("source/core/ui")
include(":core:work"); project(":core:work").projectDir = file("source/core/work")
include(":core:rootservice"); project(":core:rootservice").projectDir = file("source/core/rootservice")
include(":core:service"); project(":core:service").projectDir = file("source/core/service")
include(":core:util"); project(":core:util").projectDir = file("source/core/util")
include(":feature:main:dashboard")
include(":feature:main:list")
include(":feature:main:backup")
include(":feature:main:restore")
include(":feature:main:settings")
include(":feature:main:processing")
include(":feature:main:cloud")
include(":feature:main:history")
include(":feature:main:details")
include(":feature:main:configuration")
include(":feature:setup")
include(":feature:crash")
include(":native")
