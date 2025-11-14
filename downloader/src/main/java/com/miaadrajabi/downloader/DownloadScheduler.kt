package com.miaadrajabi.downloader

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 1. Bridges WorkManager and AlarmManager scheduling based on SchedulerConfig.
 */
internal class DownloadScheduler(
    private val context: Context,
    private val schedulerConfig: SchedulerConfig
) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * 2. Schedule a download for the configured periodic/exact cadence.
     */
    fun schedule(request: DownloadRequest, scheduleTime: ScheduleTime? = schedulerConfig.exactStartTime) {
        when {
            scheduleTime != null && schedulerConfig.useAlarmManager -> scheduleWithAlarmManager(request, scheduleTime)
            scheduleTime != null -> scheduleOneTimeWork(request, scheduleTime)
            schedulerConfig.periodicIntervalMinutes != null -> schedulePeriodic(request)
            else -> throw IllegalStateException("SchedulerConfig missing exact or periodic parameters.")
        }
    }

    /**
     * 3. Cancels scheduled jobs associated with the request id.
     */
    fun cancel(requestId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            Intent(context, DownloadAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(it)
        }
        workManager.cancelUniqueWork(uniqueWorkName(requestId))
        workManager.cancelUniqueWork(oneTimeWorkName(requestId))
    }

    private fun schedulePeriodic(request: DownloadRequest) {
        val interval = schedulerConfig.periodicIntervalMinutes ?: return
        val data = DownloadRequestAdapter.toData(request)
        val workRequest = PeriodicWorkRequestBuilder<ScheduledDownloadWorker>(
            interval, TimeUnit.MINUTES
        ).setInputData(data).build()
        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName(request.id),
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun scheduleOneTimeWork(request: DownloadRequest, scheduleTime: ScheduleTime) {
        val triggerAt = computeTriggerMillis(scheduleTime)
        val delay = triggerAt - System.currentTimeMillis()
        val builder = OneTimeWorkRequestBuilder<ScheduledDownloadWorker>()
            .setInputData(DownloadRequestAdapter.toData(request))
        if (delay > 0) {
            builder.setInitialDelay(delay, TimeUnit.MILLISECONDS)
        }
        workManager.enqueueUniqueWork(
            oneTimeWorkName(request.id),
            ExistingWorkPolicy.REPLACE,
            builder.build()
        )
    }

    private fun scheduleWithAlarmManager(request: DownloadRequest, scheduleTime: ScheduleTime) {
        val triggerAt = computeTriggerMillis(scheduleTime)
        val intent = Intent(context, DownloadAlarmReceiver::class.java).apply {
            DownloadRequestAdapter.putExtras(this, request)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            request.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (schedulerConfig.allowWhileIdle) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun computeTriggerMillis(time: ScheduleTime): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (time.year != null && time.month != null && time.dayOfMonth != null) {
            calendar.set(Calendar.YEAR, time.year)
            calendar.set(Calendar.MONTH, (time.month - 1).coerceIn(0, 11))
            calendar.set(Calendar.DAY_OF_MONTH, time.dayOfMonth)
        } else if (time.weekday != null) {
            val target = time.weekday.calendarValue
            var diff = (target - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (diff == 0 && alreadyPastToday(calendar, time)) {
                diff = 7
            }
            calendar.add(Calendar.DAY_OF_YEAR, diff)
        } else if (alreadyPastToday(calendar, time)) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        calendar.set(Calendar.HOUR_OF_DAY, time.hour)
        calendar.set(Calendar.MINUTE, time.minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        var trigger = calendar.timeInMillis
        if (trigger <= System.currentTimeMillis()) {
            trigger = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(1)
        }
        return trigger
    }

    private fun alreadyPastToday(calendar: Calendar, time: ScheduleTime): Boolean {
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        return currentHour > time.hour || (currentHour == time.hour && currentMinute >= time.minute)
    }

    private fun uniqueWorkName(requestId: String) = "${ScheduledDownloadWorker.UNIQUE_WORK_PREFIX}$requestId"

    private fun oneTimeWorkName(requestId: String) = "one-time-$requestId"
}

