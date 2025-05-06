// src/main/java/com/example/myapplication/workers/AttendanceSyncWorker.kt
package com.example.myapplication.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.example.myapplication.data.repository.DatabaseHelper

class AttendanceSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            // this will sync all pending punches
            DatabaseHelper(applicationContext).syncPendingAttendance()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
