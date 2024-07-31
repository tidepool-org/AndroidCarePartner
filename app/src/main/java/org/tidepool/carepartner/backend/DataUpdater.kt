package org.tidepool.carepartner.backend

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.tidepool.carepartner.backend.PersistentData.Companion.getAccessToken
import org.tidepool.sdk.CommunicationHelper
import org.tidepool.sdk.model.BloodGlucose
import org.tidepool.sdk.model.confirmations.Confirmation
import org.tidepool.sdk.model.data.BasalAutomatedData
import org.tidepool.sdk.model.data.BasalAutomatedData.DeliveryType
import org.tidepool.sdk.model.data.BaseData
import org.tidepool.sdk.model.data.ContinuousGlucoseData
import org.tidepool.sdk.model.data.DosingDecisionData
import org.tidepool.sdk.model.data.DosingDecisionData.CarbsOnBoard
import org.tidepool.sdk.model.data.DosingDecisionData.InsulinOnBoard
import org.tidepool.sdk.model.metadata.users.TrustUser
import org.tidepool.sdk.model.metadata.users.TrustorUser
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val TAG: String = "DataUpdater"

class DataUpdater(
    private val output: MutableState<Map<String, PillData>>,
    private val invitations: MutableState<Array<Confirmation>>,
    private val context: Context,
) : Runnable {
    
    override fun run(): Unit = runBlocking {
        Log.v(TAG, "Starting flow...")
        getIdFlow(context).map { (id, name) -> id to getData(id, name, context) }
            .collect { (id, data) ->
                val mutable = output.value.toMutableMap()
                mutable[id] = data
                output.value = mutable.toMap()
            }
        Log.v(TAG, "Flow ended!")
    }
    
    private fun getIdFlow(context: Context): Flow<Pair<String, String?>> = flow {
        val userId = communicationHelper.users.getCurrentUserInfo(context.getAccessToken()).userid
        Log.v(TAG, "Listing users...")
        val trustUsers = communicationHelper.metadata.listUsers(context.getAccessToken(), userId)
        trustUsers.filterIsInstance<TrustorUser>().filter {
            it.permissions.contains(TrustUser.Permission.view)
        }.forEach {
            emit(it.userid to it.profile?.fullName)
        }
    }
    
    private val communicationHelper: CommunicationHelper by lazy {
        CommunicationHelper(
            PersistentData.environment
        )
    }
    
    private suspend fun getGlucose(id: String, context: Context): Pair<Double?, Double?> {
        val startDate = Instant.now().minus(1, ChronoUnit.DAYS)
        val result = communicationHelper.data.getDataForUser(
            context.getAccessToken(),
            userId = id,
            type = BaseData.DataType.cbg,
            startDate = startDate
        )
        Log.v(TAG, "getData result Array Length: ${result.size}")
        val dataArr = result.filterIsInstance<ContinuousGlucoseData>().sortedByDescending { value ->
            value.time ?: Instant.MIN
        }
        
        val data = dataArr.getOrNull(0)
        val lastData = dataArr.getOrNull(1)
        val value = data?.value
        val lastValue = lastData?.value
        
        val mgdl = if (value != null) {
            data.units?.convert(value, BloodGlucose.Units.milligramsPerDeciliter) ?: value
        } else null
        
        val lastMgdl = if (lastValue != null) {
            lastData.units?.convert(lastValue, BloodGlucose.Units.milligramsPerDeciliter)
        } else null
        
        val diff = if (mgdl != null && lastMgdl != null) {
            mgdl - lastMgdl
        } else {
            null
        }
        return mgdl to diff
    }
    
    private suspend fun getBasalResult(id: String, context: Context): Double? {
        val startDate = Instant.now().minus(1, ChronoUnit.DAYS)
        val basalResult = communicationHelper.data.getDataForUser(
            context.getAccessToken(),
            userId = id,
            type = BaseData.DataType.basal,
            startDate = startDate
        )
        val basalInfo = basalResult.filterIsInstance<BasalAutomatedData>()
            .maxByOrNull { it.time ?: Instant.MIN }
        val lastAutomated = basalResult.filterIsInstance<BasalAutomatedData>()
            .filter { it.deliveryType == DeliveryType.automated }
            .maxByOrNull { it.time ?: Instant.MIN }
        val lastScheduled = basalResult.filterIsInstance<BasalAutomatedData>()
            .filter { it.deliveryType == DeliveryType.scheduled }
            .maxByOrNull { it.time ?: Instant.MIN }
        Log.v(TAG, "Basal Data: $basalInfo")
        Log.v(
            TAG,
            "Last Automated delivery: $lastAutomated (${lastAutomated?.time?.toString() ?: "No timestamp"})"
        )
        Log.v(
            TAG,
            "Last Scheduled delivery: $lastScheduled (${lastScheduled?.time?.toString() ?: "No timestamp"})"
        )
        return basalInfo?.rate
    }
    
    private suspend fun getDosingData(
        id: String,
        context: Context,
    ): Pair<CarbsOnBoard?, InsulinOnBoard?> {
        val startDate = Instant.now().minus(1, ChronoUnit.DAYS)
        val dosingResult = communicationHelper.data.getDataForUser(
            context.getAccessToken(),
            userId = id,
            type = BaseData.DataType.dosingDecision,
            startDate = startDate
        )
        return dosingResult.filterIsInstance<DosingDecisionData>()
            .maxByOrNull { it.time ?: Instant.MIN }?.let {
                Pair(it.carbsOnBoard, it.insulinOnBoard)
            } ?: Pair(null, null)
    }
    
    private suspend fun getData(id: String, name: String?, context: Context): PillData =
        coroutineScope {
            val glucoseData = async { getGlucose(id, context) }
            val basalData = async { getBasalResult(id, context) }
            val dosingData = async { getDosingData(id, context) }
            val (mgdl, diff) = glucoseData.await()
            val (activeCarbs, activeInsulin) = dosingData.await()
            return@coroutineScope PillData(
                mgdl,
                diff,
                name ?: "User",
                basalData.await(),
                activeCarbs,
                activeInsulin
            )
        }
}