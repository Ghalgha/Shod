package com.shod.client.settings.profile

/**
 * Stable display-name policy for profile slots.
 *
 * Persisted custom names remain the responsibility of the profile repository;
 * this value is only the fallback when a slot has no custom name.
 */
internal fun defaultProfileName(index: Int): String {
    return "Профиль ${index + 1}"
}
