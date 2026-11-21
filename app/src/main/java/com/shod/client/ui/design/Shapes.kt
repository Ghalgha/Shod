package com.shod.client.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
object ShodShapes {
    val Small = RoundedCornerShape(8.dp)
    val Control = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(20.dp)
    val LargeCard = RoundedCornerShape(24.dp)
    val Dialog = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

val ShodMaterialShapes = Shapes(
    extraSmall = ShodShapes.Small,
    small = ShodShapes.Small,
    medium = ShodShapes.Control,
    large = ShodShapes.Card,
    extraLarge = ShodShapes.Dialog,
)
