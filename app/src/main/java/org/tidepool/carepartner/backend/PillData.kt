package org.tidepool.carepartner.backend

import org.tidepool.carepartner.backend.WarningType.None
import org.tidepool.sdk.model.BloodGlucose.GlucoseReading
import org.tidepool.sdk.model.BloodGlucose.Trend
import org.tidepool.sdk.model.data.DosingDecisionData
import java.time.Instant

data class PillData(
    val bg: GlucoseReading? = null,
    val glucoseChange: GlucoseReading? = null,
    val name: String = "User",
    val basalRate: Double? = null,
    val activeCarbs: DosingDecisionData.CarbsOnBoard? = null,
    val activeInsulin: DosingDecisionData.InsulinOnBoard? = null,
    val lastGlucose: Instant? = null,
    val lastBolus: Instant? = null,
    val lastCarbEntry: Instant? = null,
    val trend: Trend? = null,
    val warningType: WarningType = None,
    val lastUpdate: Instant? = null,
)