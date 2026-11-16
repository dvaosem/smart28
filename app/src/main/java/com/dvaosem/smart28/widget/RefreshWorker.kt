package com.dvaosem.smart28.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Every ~15 minutes refreshes the device states shown in the widget. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        if (!SmartWidget.hasWidgets(applicationContext)) return Result.success()
        return if (SmartWidget.fetchAndRender(applicationContext)) Result.success() else Result.retry()
    }
}
