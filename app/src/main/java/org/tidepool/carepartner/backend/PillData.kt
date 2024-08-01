package org.tidepool.carepartner.backend

import org.tidepool.sdk.model.data.DosingDecisionData
import java.time.Instant

data class PillData(
    val bg: Double? = null,
    val glucoseChange: Double? = null,
    val name: String = "User",
    val basalRate: Double? = null,
    val activeCarbs: DosingDecisionData.CarbsOnBoard? = null,
    val activeInsulin: DosingDecisionData.InsulinOnBoard? = null,
    val lastGlucose: Instant? = null,
    val lastBolus: Instant? = null,
    val lastCarbEntry: Instant? = null
)