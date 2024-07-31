package org.tidepool.carepartner.backend

import org.tidepool.sdk.model.data.DosingDecisionData

data class PillData(
    val bg: Double? = null,
    val glucoseChange: Double? = null,
    val name: String = "User",
    val basalRate: Double? = null,
    val activeCarbs: DosingDecisionData.CarbsOnBoard? = null,
    val activeInsulin: DosingDecisionData.InsulinOnBoard? = null,
)