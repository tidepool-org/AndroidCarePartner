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
import org.tidepool.carepartner.backend.PersistentData.Companion.saveEmail
import org.tidepool.carepartner.backend.PersistentData.Companion.writeToDisk
import org.tidepool.sdk.CommunicationHelper
import org.tidepool.sdk.requests.Data.CommaSeparatedArray
import org.tidepool.sdk.model.BloodGlucose
import org.tidepool.sdk.model.confirmations.Confirmation
import org.tidepool.sdk.model.data.*
import org.tidepool.sdk.model.data.BasalAutomatedData.DeliveryType
import org.tidepool.sdk.model.data.BaseData.DataType.*
import org.tidepool.sdk.model.data.DosingDecisionData.CarbsOnBoard
import org.tidepool.sdk.model.data.DosingDecisionData.InsulinOnBoard
import org.tidepool.sdk.model.metadata.users.TrustUser
import org.tidepool.sdk.model.metadata.users.TrustorUser
import org.tidepool.sdk.requests.pendingCareTeamInvitations
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.measureTime

private const val TAG: String = "DataUpdater"

class DataUpdater(
    private val output: MutableState<Map<String, PillData>>,
    private val invitations: MutableState<Array<Confirmation>>,
    private val context: Context,
) : Runnable {
    
    private var savedEmail = false
    
    override fun run(): Unit = runBlocking {
        Log.v(TAG, "Starting flow...")
        getIdFlow().map { (id, name) -> id to getData(id, name) }
            .collect { (id, data) ->
                val mutable = output.value.toMutableMap()
                mutable[id] = data
                output.value = mutable.toMap()
            }
        Log.v(TAG, "Flow ended!")
        updateInvitations()
        if (!savedEmail) {
            savedEmail = true
            context.saveEmail()
        }
        context.writeToDisk()
    }
    
    suspend fun updateInvitations() {
        invitations.value = getInvitations()
    }
    
    private suspend fun getInvitations(): Array<Confirmation> {
        val userId = communicationHelper.users.getCurrentUserInfo(context.getAccessToken()).userid
        return communicationHelper.confirmations.pendingCareTeamInvitations(context.getAccessToken(), userId)
    }
    
    private fun getIdFlow(): Flow<Pair<String, String?>> = flow {
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
    
    private fun getGlucose(result: Array<BaseData>): Triple<Double?, Double?, Instant?> {
        val dataArr = result.filterIsInstance<ContinuousGlucoseData>().sortedByDescending { value ->
            value.time ?: Instant.MIN
        }
        
        val data = dataArr.getOrNull(0)
        val lastData = dataArr.getOrNull(1)
        
        val mgdl = data?.let { glucoseData ->
            glucoseData.value?.let {
                glucoseData.units?.convert(it, BloodGlucose.Units.milligramsPerDeciliter) ?: it
            }
        }
        
        val lastMgdl = lastData?.let { glucoseData ->
            glucoseData.value?.let {
                glucoseData.units?.convert(it, BloodGlucose.Units.milligramsPerDeciliter) ?: it
            }
        }
        
        val diff = mgdl?.let { curr ->
            lastMgdl?.let { last ->
                curr - last
            }
        }
        
        return Triple(mgdl, diff, data?.time)
    }
    
    private fun getBasalResult(result: Array<BaseData>): Double? {
        val basalInfo = result.filterIsInstance<BasalAutomatedData>()
            .maxByOrNull { it.time ?: Instant.MIN }
        val lastAutomated = result.filterIsInstance<BasalAutomatedData>()
            .filter { it.deliveryType == DeliveryType.automated }
            .maxByOrNull { it.time ?: Instant.MIN }
        val lastScheduled = result.filterIsInstance<BasalAutomatedData>()
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
    
    private fun getDosingData(result: Array<BaseData>): Pair<CarbsOnBoard?, InsulinOnBoard?> {
        return result.filterIsInstance<DosingDecisionData>()
            .maxByOrNull { it.time ?: Instant.MIN }?.let {
                Pair(it.carbsOnBoard, it.insulinOnBoard)
            } ?: Pair(null, null)
    }
    
    private fun getLastBolus(result: Array<BaseData>): Instant? {
        return result.filterIsInstance<BolusData>().onEach { Log.v(TAG, "Insulin data $it") }.maxByOrNull { it.time ?: Instant.MIN }?.time
    }
    
    private fun getLastCarbEntry(result: Array<BaseData>): Instant? {
        return result.filterIsInstance<FoodData>().maxByOrNull { it.time ?: Instant.MIN }?.time
    }
    
    private suspend fun getData(id: String, name: String?): PillData = coroutineScope {
            var pillData: PillData
            val timeTaken = measureTime {
                val startDate = Instant.now().minus(1, ChronoUnit.DAYS)
                Log.v(TAG, "Getting data for user $name ($id)")
                val result = communicationHelper.data.getDataForUser(
                    context.getAccessToken(),
                    userId = id,
                    types = CommaSeparatedArray(dosingDecision, basal, cbg, bolus, food),
                    startDate = startDate
                )
                Log.v(TAG, "getData result Array Length: ${result.size}")
                val glucoseData = async { getGlucose(result) }
                val basalData = async { getBasalResult(result) }
                val dosingData = async { getDosingData(result) }
                val lastBolus = async { getLastBolus(result) }
                val lastCarbEntry = async { getLastCarbEntry(result) }
                val (mgdl, diff, lastReading) = glucoseData.await()
                val (activeCarbs, activeInsulin) = dosingData.await()
                pillData = PillData(
                    mgdl,
                    diff,
                    name ?: "User",
                    basalData.await(),
                    activeCarbs,
                    activeInsulin,
                    lastReading,
                    lastBolus.await(),
                    lastCarbEntry.await()
                )
            }
            
            Log.v(TAG, "User ${pillData.name} took $timeTaken to process")
            
            return@coroutineScope pillData
        }
}