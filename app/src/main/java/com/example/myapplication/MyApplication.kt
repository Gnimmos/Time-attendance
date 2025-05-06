// src/main/java/com/example/myapplication/workers/SyncWorker.kt
package com.example.myapplication

import android.app.Application
import androidx.work.*

import java.util.concurrent.TimeUnit
import com.example.myapplication.data.repository.DatabaseHelper
import com.example.myapplication.workers.AttendanceSyncWorker


class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
//        val syncWork = PeriodicWorkRequestBuilder<AttendanceSyncWorker>(
//            10, TimeUnit.MINUTES
//        )
//            .setConstraints(
//                Constraints.Builder()
//                    .setRequiredNetworkType(NetworkType.CONNECTED)
//                    .build()
//            )
//            .build()
//
//        WorkManager.getInstance(this)
//            .enqueueUniquePeriodicWork(
//                "PendingAttendanceSync",
//                ExistingPeriodicWorkPolicy.KEEP,
//                syncWork
//            )
    }
}
