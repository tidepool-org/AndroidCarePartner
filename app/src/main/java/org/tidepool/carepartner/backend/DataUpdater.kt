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
import org.tidepool.carepartner.backend.PersistentData.Companion.getAccessToken
import org.tidepool.carepartner.backend.PersistentData.Companion.saveEmail
import org.tidepool.carepartner.backend.PersistentData.Companion.writeToDisk
import org.tidepool.carepartner.backend.WarningType.*
import org.tidepool.sdk.CommunicationHelper
import org.tidepool.sdk.model.BloodGlucose.GlucoseReading
import org.tidepool.sdk.model.BloodGlucose.Trend
import org.tidepool.sdk.model.confirmations.Confirmation
import org.tidepool.sdk.model.data.*
import org.tidepool.sdk.model.data.BasalAutomatedData.DeliveryType
import org.tidepool.sdk.model.data.BaseData.DataType.*
import org.tidepool.sdk.model.data.DosingDecisionData.CarbsOnBoard
import org.tidepool.sdk.model.data.DosingDecisionData.InsulinOnBoard
import org.tidepool.sdk.model.metadata.users.TrustUser
import org.tidepool.sdk.model.metadata.users.TrustorUser
import org.tidepool.sdk.model.mgdl
import org.tidepool.sdk.requests.Data.CommaSeparatedArray
import org.tidepool.sdk.requests.accept
import org.tidepool.sdk.requests.dismiss
import org.tidepool.sdk.requests.receivedInvitations
import retrofit2.HttpException
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.measureTime

private const val TAG: String = "DataUpdater"

class DataUpdater(
    output: MutableState<Map<String, PillData>>,
    invitations: MutableState<Array<Confirmation>>,
    error: MutableState<Exception?>,
    private val context: Context,
) : Runnable {
    
    private var output by output
    private var invitations by invitations
    private var error by error
    
    private var savedEmail = false
    
    private suspend fun runAsync() {
        try {
            Log.v(TAG, "Starting flow...")
            getIdFlow().map { (id, name) -> id to getData(id, name) }
                .collect { (id, data) ->
                    val mutable = output.toMutableMap()
                    mutable[id] = data
                    output = mutable.toMap()
                }
            Log.v(TAG, "Flow ended!")
            updateInvitations()
            if (!savedEmail) {
                savedEmail = true
                context.saveEmail()
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
        communicationHelper.confirmations.accept(
            context.getAccessToken(),
            communicationHelper.users.getCurrentUserInfo(
                context.getAccessToken()
            ).userid,
            confirmation
        )
        updateInvitations()
        runAsync()
    }
    
    suspend fun rejectConfirmation(confirmation: Confirmation) {
        communicationHelper.confirmations.dismiss(
            context.getAccessToken(),
            communicationHelper.users.getCurrentUserInfo(
                context.getAccessToken()
            ).userid,
            confirmation
        )
        updateInvitations()
    }
    
    internal open class FatalDataException protected constructor(
        message: String = "A Fatal Exception Occurred",
        e: Exception? = null
    ) : Exception(message, e) {
        
        constructor(exception: Exception? = null) : this(e = exception)
    }
    
    internal class ServerError(e: Exception? = null): FatalDataException("Server Error", e)
    
    internal class TokenExpiredException(e: Exception? = null) :
        FatalDataException("The Token Has Expired", e)
    
    internal class NoAccessException(e: Exception? = null) : FatalDataException("No Access to Data", e)
    
    suspend fun updateInvitations() {
        invitations = getInvitations()
    }
    
    private suspend fun getInvitations(): Array<Confirmation> {
        val userId = communicationHelper.users.getCurrentUserInfo(context.getAccessToken()).userid
        return communicationHelper.confirmations.receivedInvitations(
            context.getAccessToken(),
            userId
        )
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
    
    private fun getGlucose(result: Array<BaseData>): GlucoseData {
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
                value > 250.mgdl -> Warning
                value in 55.mgdl..<70.mgdl   -> Warning
                value < 55.mgdl              -> Critical
                else                         -> None
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
        return result.filterIsInstance<BolusData>().maxByOrNull { it.time ?: Instant.MIN }?.time
    }
    
    private fun getLastCarbEntry(result: Array<BaseData>): Instant? {
        return result.filterIsInstance<FoodData>().maxByOrNull { it.time ?: Instant.MIN }?.time
    }
    
    private suspend fun getData(id: String, name: String?): PillData = coroutineScope {
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
            Log.v(TAG, "Getting data for user $name ($id)")
            val longJob = launch {
                val startDate = Instant.now().minus(3, ChronoUnit.DAYS)
                val result = communicationHelper.data.getDataForUser(
                    context.getAccessToken(),
                    userId = id,
                    types = CommaSeparatedArray(bolus, food),
                    startDate = startDate
                )
                
                val lastBolusDeferred = async { getLastBolus(result) }
                val lastCarbEntryDeferred = async { getLastCarbEntry(result) }
                lastBolus = lastBolusDeferred.await()
                lastCarbEntry = lastCarbEntryDeferred.await()
            }
            val shortJob = launch {
                val startDate = Instant.now().minus(630, ChronoUnit.SECONDS) // - 10.5 minutes
                
                val result = communicationHelper.data.getDataForUser(
                    context.getAccessToken(),
                    userId = id,
                    types = CommaSeparatedArray(dosingDecision, basal, cbg),
                    startDate = startDate
                )
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