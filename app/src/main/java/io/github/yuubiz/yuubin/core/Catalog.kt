package io.github.yuubiz.yuubin.core

object Catalog {
    val folderPlayerFork = CatalogApp(
        packageName = "com.wing.folderplayer.fork",
        displayName = "Folder Player Fork",
        repo = GitHubRepo("yuu-biz", "Folder-Player"),
        apkAsset = Regex("""FolderPlayerFork-\d+\.\d+\.\d+\.apk"""),
        signer = pin("bd7e99204e5596526c83407164e543ab29f7ce77add916cab476303436da3c1a"),
    )

    val yuubin = CatalogApp(
        packageName = "io.github.yuubiz.yuubin",
        displayName = "Yuubin",
        repo = GitHubRepo("yuu-biz", "self-distributor"),
        apkAsset = Regex("""Yuubin-\d+\.\d+\.\d+\.apk"""),
        signer = pin("9d9b36b35d1738324a1298ce2ee177856b71e154f4fc4e83e4626770f4d46ba6"),
    )

    val apps: List<CatalogApp> = listOf(folderPlayerFork, yuubin)

    private fun pin(hex: String): Sha256 = requireNotNull(Sha256.parse(hex)) { "bad pin $hex" }
}
