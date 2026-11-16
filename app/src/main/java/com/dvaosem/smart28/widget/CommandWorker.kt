package com.dvaosem.smart28.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Runs one delayed device command (e.g. returning the thermostat to its previous temperature after a boost). */
class CommandWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val code = inputData.getString("code") ?: return Result.failure()
        val value = inputData.getInt("value", Int.MIN_VALUE)
        if (value == Int.MIN_VALUE) return Result.failure()
        val cmds = JSONArray().put(JSONObject().put("code", code).put("value", value))
        val ok = S28Api.call(applicationContext, "command", JSONObject().put("id", id).put("commands", cmds)) != null
        return if (ok) Result.success() else if (runAttemptCount < 5) Result.retry() else Result.failure()
    }

    companion object {
        fun schedule(c: Context, id: String, code: String, value: Int, delayMin: Int) {
            val req = OneTimeWorkRequestBuilder<CommandWorker>()
                .setInitialDelay(delayMin.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf("id" to id, "code" to code, "value" to value))
                .build()
            WorkManager.getInstance(c).enqueueUniqueWork("cmd-$id-$code", ExistingWorkPolicy.REPLACE, req)
        }
        fun cancel(c: Context, id: String, code: String) {
            WorkManager.getInstance(c).cancelUniqueWork("cmd-$id-$code")
        }
    }
}
