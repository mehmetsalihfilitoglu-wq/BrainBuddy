package com.edumio.app.core

/**
 * Resolves package names to friendly display names for blocked app attempt reports.
 * No hardcoded package names - use AppLabelResolver.getLabel(context, pkg) for resolution.
 */
object PackageNameHelper {

    /** Use AppLabelResolver.getLabel(context, pkg) instead. Returns generic fallback. */
    @Deprecated("Use AppLabelResolver.getLabel(context, pkg) to resolve from PackageManager")
    fun getFriendlyName(@Suppress("UNUSED_PARAMETER") pkg: String): String = "Uygulama"
}
