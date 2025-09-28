package org.tidepool.carepartner.backend

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.tidepool.carepartner.TidepoolApplication
import org.tidepool.carepartner.backend.PersistentData.Companion.saveEmail
import org.tidepool.carepartner.backend.PersistentData.Companion.writeToDisk
import org.tidepool.carepartner.backend.WarningType.*
import org.tidepool.carepartner.filterList
import org.tidepool.sdk.TidepoolSDK
import org.tidepool.sdk.model.BloodGlucose.GlucoseReading
import org.tidepool.sdk.model.BloodGlucose.Trend
import org.tidepool.sdk.model.confirmations.Confirmation
import org.tidepool.sdk.model.data.*
import org.tidepool.sdk.model.data.BasalAutomatedData.DeliveryType
import org.tidepool.sdk.model.data.DataType.*
import org.tidepool.sdk.model.data.DosingDecisionData.CarbsOnBoard
import org.tidepool.sdk.model.data.DosingDecisionData.InsulinOnBoard
import org.tidepool.sdk.model.metadata.users.Permission
import org.tidepool.sdk.model.metadata.users.TrustUser
import org.tidepool.sdk.model.mgdl
import org.tidepool.sdk.service.ConfirmationService
import retrofit2.HttpException
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.measureTime

private const val TAG: String = "DataUpdater"

class DataUpdater(
    output: MutableState<Map<String, PillData>>,
    invitations: MutableState<List<Confirmation>>,
    error: MutableState<Throwable?>,
    private val context: Context,
) : Runnable {
    
    private var output by output
    private var invitations by invitations
    private var error by error
    
    private var savedEmail = false
    
    private val tidepoolSDK: TidepoolSDK by lazy {
        TidepoolApplication.getTidepoolSDK(context)
    }
    
    private suspend fun runAsync() {
        try {
            Log.v(TAG, "Starting flow...")
            getUserIdFlow().map { (id, name) -> id to getData(id, name) }
                .collect { (id, data) ->
                    val mutable = output.toMutableMap()
                    mutable[id] = data
                    output = mutable.toMap()
                }
            Log.v(TAG, "Flow ended!")
            updateInvitations()
            if (!savedEmail) {
                savedEmail = true
                tidepoolSDK.users.getCurrentUser().fold(
                    onSuccess = {
                        context.saveEmail(
                            name = it.profile?.fullName.orEmpty(),
                            email = it.username.orEmpty(),
                        )
                    },
                    onFailure = {},
                )
            }
            context.writeToDisk()
        } catch (e: HttpException) {
            error = when (e.code()) {
                401      -> TokenExpiredException(e)
                403, 451 -> NoAccessException(e)
                505      -> ServerError(e)
                else     -> e
            }
        } catch (e: Exception) {
            error = e
        }
    }
    
    override fun run(): Unit = runBlocking {
        runAsync()
    }
    
    suspend fun acceptConfirmation(confirmation: Confirmation) {
        tidepoolSDK.confirmations.acceptConfirmation(
            confirmationKey = confirmation.key,
            creatorId = confirmation.creatorId,
        ).fold(
            onSuccess = { Log.d(TAG, "onAcceptConfirmationSuccess()") },
            onFailure = {
                Log.e(TAG, "onAcceptConfirmationFailure(): ", it)
                error = it
            },
        )
        updateInvitations()
        runAsync()
    }
    
    suspend fun rejectConfirmation(confirmation: Confirmation) {
        tidepoolSDK.confirmations.dismissConfirmation(
            confirmationKey = confirmation.key,
            creatorId = confirmation.creatorId
        ).fold(
            onSuccess = {
                updateInvitations()
            },
            onFailure = {
                Log.e(TAG, "onAcceptConfirmationFailure(): ", it)
                error = it
            },
        )
    }
    
    internal open class FatalDataException protected constructor(
        message: String = "A Fatal Exception Occurred",
        e: Exception? = null
    ) : Exception(message, e) {
        
        constructor(exception: Exception? = null) : this(e = exception)
    }
    
    internal class ServerError(e: Exception? = null) : FatalDataException("Server Error", e)
    
    internal class TokenExpiredException(e: Exception? = null) :
        FatalDataException("The Token Has Expired", e)
    
    internal class NoAccessException(e: Exception? = null) :
        FatalDataException("No Access to Data", e)
    
    suspend fun updateInvitations() {
        tidepoolSDK.confirmations.getReceivedInvitations().fold(
            onSuccess = {
                invitations = it
            },
            onFailure = {
                Log.e(TAG, "onUpdateConfirmationFailure(): ", it)
                error = it
            },
        )
    }
    
    private fun getUserIdFlow(): Flow<Pair<String, String?>> = flow {
        tidepoolSDK.metadata.getTrustUsers()
            .filterList { it is TrustUser.TrustorUser && it.permissions.contains(Permission.View) }
            .fold(
                onSuccess = { viewers ->
                    viewers.forEach { emit(it.userId to it.profile?.fullName) }
                },
                onFailure = {
                    Log.e(TAG, "Failed to get trust users: ", it)
                    error = it
                },
            )
    }
    
    private fun getGlucose(result: List<BaseData>): GlucoseData {
        // >400 -> critical
        // 250..400 -> warning
        // 55..70 -> warning
        // < 55 -> critical
        val dataArr = result.filterIsInstance<ContinuousGlucoseData>().sortedByDescending { value ->
            value.time ?: Instant.MIN
        }
        
        val data = dataArr.getOrNull(0)
        Log.v(TAG, "Data: $data")
        val lastData = dataArr.getOrNull(1)
        
        val warningType = data?.reading?.let { value ->
            when {
                value > 250.mgdl           -> Warning
                value in 55.mgdl..<70.mgdl -> Warning
                value < 55.mgdl            -> Critical
                else                       -> None
            }
        } ?: None
        
        val diff = data?.reading?.let { curr ->
            lastData?.reading?.let { last ->
                curr - last
            }
        }
        
        return GlucoseData(data?.reading, diff, data?.time, data?.trend, warningType)
    }
    
    private data class GlucoseData(
        val mgdl: GlucoseReading?,
        val diff: GlucoseReading?,
        val time: Instant?,
        val trend: Trend?,
        val warningType: WarningType = None
    )
    
    private fun getBasalResult(result: List<BaseData>): Double? {
        val basalInfo = result.filterIsInstance<BasalAutomatedData>()
            .maxByOrNull { it.time ?: Instant.MIN }
        val lastAutomated = result.filterIsInstance<BasalAutomatedData>()
            .filter { it.deliveryType == DeliveryType.Automated }
            .maxByOrNull { it.time ?: Instant.MIN }
        val lastScheduled = result.filterIsInstance<BasalAutomatedData>()
            .filter { it.deliveryType == DeliveryType.Scheduled }
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
    
    private fun getDosingData(result: List<BaseData>): Pair<CarbsOnBoard?, InsulinOnBoard?> {
        return result.filterIsInstance<DosingDecisionData>()
            .maxByOrNull { it.time ?: Instant.MIN }?.let {
                Pair(it.carbsOnBoard, it.insulinOnBoard)
            } ?: Pair(null, null)
    }
    
    private fun getLastBolus(result: List<BaseData>): Instant? {
        return result.filterIsInstance<BolusData>().maxByOrNull { it.time ?: Instant.MIN }?.time
    }
    
    private fun getLastCarbEntry(result: List<BaseData>): Instant? {
        return result.filterIsInstance<FoodData>().maxByOrNull { it.time ?: Instant.MIN }?.time
    }
    
    private suspend fun getData(userId: String, name: String?): PillData = coroutineScope {
        var pillData: PillData
        val timeTaken = measureTime {
            var lastBolus: Instant? = null
            var lastCarbEntry: Instant? = null
            var mgdl: GlucoseReading? = null
            var diff: GlucoseReading? = null
            var lastReading: Instant? = null
            var activeCarbs: CarbsOnBoard? = null
            var activeInsulin: InsulinOnBoard? = null
            var basalRate: Double? = null
            lateinit var warningType: WarningType
            var trend: Trend? = null
            Log.v(TAG, "Getting data for user $name ($userId)")
            val longJob = launch {
                val startDate = Instant.now().minus(3, ChronoUnit.DAYS)
                tidepoolSDK.data.getDataForUser(
                    userId = userId,
                    uploadId = null,
                    deviceId = null,
                    types = listOf(Food, Bolus),
                    startDate = startDate,
                    endDate = null,
                    latest = null,
                    dexcom = null,
                    carelink = null,
                    medtronic = null,
                ).fold(
                    onSuccess = {
                        val lastBolusDeferred = async { getLastBolus(it) }
                        val lastCarbEntryDeferred = async { getLastCarbEntry(it) }
                        lastBolus = lastBolusDeferred.await()
                        lastCarbEntry = lastCarbEntryDeferred.await()
                    },
                    onFailure = {},
                )
            }
            val shortJob = launch {
                val startDate = Instant.now().minus(630, ChronoUnit.SECONDS) // - 10.5 minutes
                
                tidepoolSDK.data.getDataForUser(
                    userId = userId,
                    uploadId = null,
                    deviceId = null,
                    types = listOf(Cbg, Basal, DosingDecision),
                    startDate = startDate,
                    endDate = null,
                    latest = null,
                    dexcom = null,
                    carelink = null,
                    medtronic = null,
                ).fold(
                    onSuccess = { result ->
                        Log.v(TAG, "getData result Array Length: ${result.size}")
                        val glucoseData = async { getGlucose(result) }
                        val basalData = async { getBasalResult(result) }
                        val dosingData = async { getDosingData(result) }
                        
                        val (newMgdl, newDiff, newLastReading, newTrend, newWarningType) = glucoseData.await()
                        
                        mgdl = newMgdl
                        diff = newDiff
                        lastReading = newLastReading
                        trend = newTrend
                        warningType = newWarningType
                        val (newActiveCarbs, newActiveInsulin) = dosingData.await()
                        activeCarbs = newActiveCarbs
                        activeInsulin = newActiveInsulin
                        basalRate = basalData.await()
                    },
                    onFailure = {},
                )
            }
            
            longJob.join()
            shortJob.join()
            
            pillData = PillData(
                mgdl,
                diff,
                name ?: "User",
                basalRate,
                activeCarbs,
                activeInsulin,
                lastReading,
                lastBolus,
                lastCarbEntry,
                trend,
                warningType,
                arrayOf(lastReading, lastBolus, lastCarbEntry).filterNotNull().maxOrNull()
            )
        }
        
        Log.v(TAG, "User ${pillData.name} took $timeTaken to process")
        
        return@coroutineScope pillData
    }
}