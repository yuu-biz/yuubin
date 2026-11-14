package io.github.yuubiz.yuubin.core

object Catalog {
    val folderPlayerFork = CatalogApp(
        packageName = "com.wing.folderplayer.fork",
        displayName = "Folder Player Fork",
        repo = GitHubRepo("yuu-biz", "Folder-Player"),
        apkAsset = Regex("""FolderPlayerFork-\d+\.\d+\.\d+\.apk"""),
        signer = pin("bd7e99204e5596526c83407164e543ab29f7ce77add916cab476303436da3c1a"),
    )

    val folirel = CatalogApp(
        packageName = "io.github.yuubiz.folirel",
        displayName = "Folirel",
        repo = GitHubRepo("yuu-biz", "folirel"),
        apkAsset = Regex("""Folirel-\d+\.\d+\.\d+\.apk"""),
        signer = pin("c740ae3b5a57bf46be12edd7894b6a017095b95a3f7217e2cc4b9402cc9b00b1"),
    )

    val hirari = CatalogApp(
        packageName = "io.github.yuubiz.hirari",
        displayName = "Hirari",
        repo = GitHubRepo("yuu-biz", "hirari"),
        apkAsset = Regex("""Hirari-\d+\.\d+\.\d+\.apk"""),
        signer = pin("1116351eafe09b6f99bff277b959ec1700e12aed7bd1bfd463c534ba2d574441"),
    )

    val yuubin = CatalogApp(
        packageName = "io.github.yuubiz.yuubin",
        displayName = "Yuubin",
        repo = GitHubRepo("yuu-biz", "yuubin"),
        apkAsset = Regex("""Yuubin-\d+\.\d+\.\d+\.apk"""),
        signer = pin("9d9b36b35d1738324a1298ce2ee177856b71e154f4fc4e83e4626770f4d46ba6"),
    )

    val apps: List<CatalogApp> = listOf(folderPlayerFork, folirel, hirari, yuubin)

    private fun pin(hex: String): Sha256 = requireNotNull(Sha256.parse(hex)) { "bad pin $hex" }
}
