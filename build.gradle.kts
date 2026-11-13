plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied to :app (AGP 9 built-in Kotlin). Declared only to pin the KGP version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
