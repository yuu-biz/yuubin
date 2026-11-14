package io.github.yuubiz.yuubin.core

/**
 * What the background check posts. [apps] are every installed catalog app with an update available, in catalog
 * order; [notified] is the package-to-tag record to store once the notification is out.
 */
data class UpdateNotice(val apps: List<CatalogApp>, val notified: Map<String, String>)

/**
 * Returns the notice to post, or null when every available update was notified before. [notified] maps a package
 * name to the release tag last notified for it, so each (package, version) triggers one notification however often
 * the check runs. The notice lists all available updates, not only the new ones, because it replaces the previous one.
 *
 * Entries are never dropped: a package whose lookup failed this time keeps its record and is not announced again.
 */
fun updateNotice(statuses: List<Pair<CatalogApp, AppStatus?>>, notified: Map<String, String>): UpdateNotice? {
    val updates = statuses.mapNotNull { (app, status) -> (status as? AppStatus.UpdateAvailable)?.let { app to it.release.tag } }
    if (updates.none { (app, tag) -> notified[app.packageName] != tag }) return null
    return UpdateNotice(
        apps = updates.map { it.first },
        notified = notified + updates.map { (app, tag) -> app.packageName to tag },
    )
}
