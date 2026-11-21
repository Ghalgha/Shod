package com.shod.client.deploy.plan

import com.shod.client.deploy.inventory.FoundServer

internal enum class PlanKind { INSTALL, UPDATE, REPLACE }

internal data class PlanAction(
    val kind: PlanKind,
    val peerPort: Int,
    val webPort: Int,
    val title: String,
    val detail: String,
) {
    val destructive: Boolean get() = kind == PlanKind.REPLACE
}

internal data class DeployPlan(
    val servers: List<FoundServer>,
    val actions: List<PlanAction>,
    val notes: List<String>,
)
