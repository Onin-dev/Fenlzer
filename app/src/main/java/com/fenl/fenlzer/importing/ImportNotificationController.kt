package com.fenl.fenlzer.importing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.fenl.fenlzer.MainActivity
import com.fenl.fenlzer.R
import com.fenl.fenlzer.data.local.dao.ImportDao

class ImportNotificationController(
    private val context: Context,
    private val importDao: ImportDao
) {
    suspend fun foregroundInfo(): ForegroundInfo {
        createChannel()
        val jobs = importDao.getRunnableJobs()
        val active = jobs.filterNot { it.status == "QUEUED" }
        val upcoming = jobs.count { it.status == "QUEUED" }
        val lines = jobs.take(5).map { job ->
            val title = job.technicalDetailsJson ?: job.youtubeVideoId ?: "Import"
            "$title · ${job.status.displayStatus()}"
        }
        val title = when (jobs.size) {
            0 -> "Finishing imports"
            1 -> "1 import active"
            else -> "${jobs.size} imports active"
        }
        val summary = buildString {
            append("${active.size} downloading now")
            if (upcoming > 0) append(" · $upcoming upcoming")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.InboxStyle().also { style ->
                lines.forEach(style::addLine)
                style.setSummaryText(summary)
            })
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        action = ACTION_OPEN_ACTIVE_IMPORTS
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOnlyAlertOnce(true)
            .setOngoing(jobs.isNotEmpty())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Imports",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress for local and YouTube imports"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val ACTION_OPEN_ACTIVE_IMPORTS = "com.fenl.fenlzer.OPEN_ACTIVE_IMPORTS"
        private const val CHANNEL_ID = "fenlzer_imports"
        private const val NOTIFICATION_ID = 41_017
    }
}

private fun String.displayStatus(): String =
    lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
