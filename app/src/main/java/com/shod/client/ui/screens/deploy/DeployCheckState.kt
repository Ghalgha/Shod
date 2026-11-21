package com.shod.client.ui.screens.deploy

import androidx.compose.runtime.mutableStateOf
import com.shod.client.deploy.plan.DeployPlan
import com.shod.client.deploy.plan.PlanAction

/** Kept above tab content so results arriving while Logs is open are not discarded. */
internal class DeployCheckState {
    val plan = mutableStateOf<DeployPlan?>(null)
    val replacement = mutableStateOf<PlanAction?>(null)
}
