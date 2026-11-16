package com.dvaosem.smart28.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Every ~15 minutes downloads the device states and raises phone notifications by the user's rules. */
class AlertWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val c = applicationContext
        if (!Notifier.settings(c).optBoolean("on", false)) return Result.success()
        val arr = S28Api.call(c, "devices")?.optJSONArray("devices") ?: return Result.retry()
        Notifier.check(c, arr)
        return Result.success()
    }
}
