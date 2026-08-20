pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // pixiv-login (com.github.SoxiaLiSA) 由 JitPack 发布
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PixivReader"

include(":app")

// vendor 的 pixiv-api-kotlin 构建模块
include(":lib:pixivapi")

// core 层
include(":core:common")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:ui")
include(":core:novel")

// feature 层
include(":feature:auth")
include(":feature:home")
include(":feature:discover")
include(":feature:comments")
include(":feature:illust")
include(":feature:viewer")
include(":feature:novel")
include(":feature:reader")
include(":feature:user")
include(":feature:bookmark")
include(":feature:watchlist")
include(":feature:manga")
include(":feature:follow")
include(":feature:onboarding")
