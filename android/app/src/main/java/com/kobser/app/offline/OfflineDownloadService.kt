package com.kobser.app.offline

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.kobser.app.MainActivity
import com.kobser.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps "Keep offline" downloads running with the app in
 * the background, showing their progress as a notification. The queue itself
 * lives in [OfflineManager.downloadManager] and is persisted by Media3, so
 * downloads survive process death and resume when the service restarts.
 */
@UnstableApi
@AndroidEntryPoint
class OfflineDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.offline_downloads_channel,
    0,
) {
    @Inject
    lateinit var offline: OfflineManager

    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
        NotificationUtil.createNotificationChannel(
            this, CHANNEL_ID, R.string.offline_downloads_channel, 0, NotificationUtil.IMPORTANCE_LOW,
        )
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager = offline.downloadManager

    // No reboot-time rescheduling; the queue resumes the next time the app runs.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val message = when {
            notMetRequirements and Requirements.NETWORK != 0 -> getString(R.string.offline_downloads_waiting_network)
            else -> getString(R.string.offline_downloads_progress, downloads.size)
        }
        return notificationHelper.buildProgressNotification(
            this, R.drawable.ic_notification, open, message, downloads, notMetRequirements,
        )
    }

    companion object {
        const val CHANNEL_ID = "offline_downloads"
        private const val NOTIFICATION_ID = 2001
    }
}
