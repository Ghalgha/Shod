package com.shod.client.integration.vk.auth.model

internal data class VkAuthPayload(
    val token: String,
    val userId: String,
    val expiresIn: Long,
)

internal enum class VkAuthPhase { LOGIN, TOKEN }
