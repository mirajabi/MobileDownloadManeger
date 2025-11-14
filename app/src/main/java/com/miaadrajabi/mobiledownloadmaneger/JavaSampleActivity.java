package com.miaadrajabi.mobiledownloadmaneger;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.miaadrajabi.downloader.DownloadConfig;
import com.miaadrajabi.downloader.DownloadDestination;
import com.miaadrajabi.downloader.DownloadForegroundService;
import com.miaadrajabi.downloader.DownloadHandle;
import com.miaadrajabi.downloader.DownloadListener;
import com.miaadrajabi.downloader.DownloadProgress;
import com.miaadrajabi.downloader.DownloadRequest;
import com.miaadrajabi.downloader.RetryPolicy;
import com.miaadrajabi.downloader.ScheduleTime;
import com.miaadrajabi.downloader.SchedulerConfig;
import com.miaadrajabi.downloader.StorageConfig;
import com.miaadrajabi.downloader.StorageResolver;
import com.miaadrajabi.downloader.StorageResolution;
import com.miaadrajabi.downloader.StorageResolutionException;
import com.miaadrajabi.downloader.Weekday;
import com.miaadrajabi.downloader.ChunkingConfig;
import com.miaadrajabi.downloader.NotificationConfig;
import com.miaadrajabi.downloader.InstallerConfig;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class JavaSampleActivity extends AppCompatActivity {

    private static final long MIN_UI_INTERVAL_MS = 750L;
    private static final long MIN_UI_BYTES_STEP = 128 * 1024L;
    private static final String SAMPLE_URL = "https://www.dl.farsroid.com/ap/Chrome-142.0.7444.139-32(FarsRoid.Com).apks";
    private static final String TAG = "JavaSampleActivity";

    private TextView summaryView;
    private TextView instructionsView;
    private TextView statusView;
    private TextView scheduleStatusView;
    private Button pauseButton;
    private Button resumeButton;

    private String currentHandleId;
    private long lastUiBytes;
    private long lastUiTimestamp;

    private final DownloadListener listener = new DownloadListener() {
        @Override
        public void onQueued(DownloadHandle handle) {
            Log.d(TAG, "Queued " + handle.getId());
            currentHandleId = handle.getId();
            updateStatus("Queued: " + handle.getId());
            setPauseResumeState(true, false);
        }

        @Override
        public void onStarted(DownloadHandle handle) {
            updateStatus("Started: " + handle.getId());
        }

        @Override
        public void onProgress(DownloadHandle handle, DownloadProgress progress) {
            long now = System.currentTimeMillis();
            long deltaBytes = progress.getBytesDownloaded() - lastUiBytes;
            long deltaTime = now - lastUiTimestamp;
            if (deltaBytes < MIN_UI_BYTES_STEP && deltaTime < MIN_UI_INTERVAL_MS) {
                return;
            }
            lastUiBytes = progress.getBytesDownloaded();
            lastUiTimestamp = now;

            String total = progress.getTotalBytes() != null
                    ? "/" + toHumanReadable(progress.getTotalBytes())
                    : "";
            String speed = progress.getBytesPerSecond() != null
                    ? " • " + toHumanReadable(progress.getBytesPerSecond()) + "/s"
                    : "";
            updateStatus("Downloading: " + toHumanReadable(progress.getBytesDownloaded()) + total + speed);
        }

        @Override
        public void onPaused(DownloadHandle handle) {
            updateStatus("Paused: " + handle.getId());
            setPauseResumeState(false, true);
        }

        @Override
        public void onResumed(DownloadHandle handle) {
            updateStatus("Resuming: " + handle.getId());
            setPauseResumeState(true, false);
        }

        @Override
        public void onCompleted(DownloadHandle handle) {
            updateStatus("Completed: " + handle.getId());
            currentHandleId = null;
            setPauseResumeState(false, false);
        }

        @Override
        public void onFailed(DownloadHandle handle, Throwable error) {
            updateStatus("Failed: " + (error != null ? error.getMessage() : "unknown"));
            currentHandleId = null;
            setPauseResumeState(false, false);
        }

        @Override
        public void onRetry(DownloadHandle handle, int attempt) {
            Log.d(TAG, "Retry " + attempt + " for " + handle.getId());
        }

        @Override
        public void onCancelled(DownloadHandle handle) {
            updateStatus("Cancelled: " + handle.getId());
            currentHandleId = null;
            setPauseResumeState(false, false);
        }
    };

    private Runnable pendingPermissionAction;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DownloadForegroundService.setNotificationIcon(R.mipmap.ic_launcher);

        summaryView = findViewById(R.id.tvBuilderSummary);
        instructionsView = findViewById(R.id.tvInstructions);
        statusView = findViewById(R.id.tvDownloadStatus);
        scheduleStatusView = findViewById(R.id.tvScheduleStatus);
        Button startButton = findViewById(R.id.btnStartDownload);
        Button scheduleButton = findViewById(R.id.btnScheduleDownload);
        Button exactButton = findViewById(R.id.btnScheduleExact);
        pauseButton = findViewById(R.id.btnPauseDownload);
        resumeButton = findViewById(R.id.btnResumeDownload);

        DownloadConfig previewConfig = buildPreviewConfig();
        summaryView.setText(buildSummary(previewConfig));
        instructionsView.setText(buildStoragePreview(previewConfig));
        statusView.setText("Idle — tap the button to start a download.");
        scheduleStatusView.setText(getString(R.string.sample_schedule_hint));
        setPauseResumeState(false, false);

        DownloadForegroundService.registerListener(listener);

        startButton.setOnClickListener(v -> ensureStoragePermission(() -> {
            DownloadRequest request = createSampleRequest();
            currentHandleId = request.getId();
            DownloadForegroundService.enqueueDownload(this, request);
            updateStatus("Queued: " + request.getId());
            setPauseResumeState(true, false);
        }));

        pauseButton.setOnClickListener(v -> {
            if (currentHandleId != null) {
                DownloadForegroundService.pauseDownload(this, currentHandleId);
            }
        });

        resumeButton.setOnClickListener(v -> {
            if (currentHandleId != null) {
                ensureStoragePermission(() ->
                        DownloadForegroundService.resumeDownload(this, currentHandleId)
                );
            }
        });

        scheduleButton.setOnClickListener(v -> ensureStoragePermission(() -> {
            ScheduleTime scheduleTime = new ScheduleTime(0, 30, Weekday.TUESDAY, null, null, null);
            DownloadRequest request = createScheduledRequest("weekday");
            DownloadForegroundService.scheduleDownload(this, request, scheduleTime);
            scheduleStatusView.setText("Scheduled " + request.getFileName() + " for " + describeSchedule(scheduleTime));
        }));

        exactButton.setOnClickListener(v -> ensureStoragePermission(() -> {
            ScheduleTime scheduleTime = createExactScheduleTime();
            DownloadRequest request = createScheduledRequest("exact");
            DownloadForegroundService.scheduleDownload(this, request, scheduleTime);
            scheduleStatusView.setText("Scheduled " + request.getFileName() + " for " + describeSchedule(scheduleTime));
        }));
    }

    @Override
    protected void onDestroy() {
        DownloadForegroundService.unregisterListener(listener);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSIONS) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) {
                granted &= (result == PackageManager.PERMISSION_GRANTED);
            }
            if (granted && pendingPermissionAction != null) {
                pendingPermissionAction.run();
            } else if (!granted) {
                updateStatus("Storage permission is required to write into Downloads folder.");
            }
            pendingPermissionAction = null;
        }
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> statusView.setText(message));
    }

    private void setPauseResumeState(boolean canPause, boolean canResume) {
        runOnUiThread(() -> {
            pauseButton.setEnabled(canPause);
            resumeButton.setEnabled(canResume);
        });
    }

    private DownloadRequest createSampleRequest() {
        return buildDownloadRequest("TMS-SADAD");
    }

    private DownloadRequest createScheduledRequest(String prefix) {
        return buildDownloadRequest(prefix);
    }

    private DownloadRequest buildDownloadRequest(String prefix) {
        String baseName = extractBaseName(SAMPLE_URL);
        String stamp = formattedTimestamp();
        String fileName = prefix + "_" + stamp + "_" + baseName;
        return new DownloadRequest(
                SAMPLE_URL,
                fileName,
                DownloadDestination.Auto.INSTANCE,
                UUID.randomUUID().toString(),
                Collections.emptyMap()
        );
    }

    private DownloadConfig buildPreviewConfig() {
        ChunkingConfig chunkingConfig = new ChunkingConfig(4, 256 * 1024L, true);
        RetryPolicy retryPolicy = new RetryPolicy(5, 3_000L, 1.5f);
        NotificationConfig notificationConfig = new NotificationConfig(
                "sample_downloads",
                "Sample Downloads",
                "Foreground sample channel",
                true,
                true,
                R.mipmap.ic_launcher
        );
        SchedulerConfig schedulerConfig = new SchedulerConfig(60L, null, true, false);
        StorageConfig storageConfig = new StorageConfig(
                Collections.singletonList(DownloadDestination.Auto.INSTANCE),
                true,
                true,
                10 * 1024 * 1024L,
                true
        );
        InstallerConfig installerConfig = new InstallerConfig(true, true, "application/vnd.android.package-archive");
        return new DownloadConfig(
                chunkingConfig,
                retryPolicy,
                true,
                notificationConfig,
                schedulerConfig,
                storageConfig,
                installerConfig,
                Collections.emptyList()
        );
    }

    private String buildSummary(DownloadConfig config) {
        StringBuilder builder = new StringBuilder();
        builder.append("Chunk count: ").append(config.getChunking().getChunkCount()).append('\n');
        builder.append("Min chunk size: ").append(toHumanReadable(config.getChunking().getMinChunkSizeBytes())).append('\n');
        builder.append("Retry attempts: ").append(config.getRetryPolicy().getMaxAttempts()).append('\n');
        builder.append("Notification channel: ").append(config.getNotification().getChannelId()).append('\n');
        builder.append("Foreground enforced: ").append(config.getEnforceForegroundService()).append('\n');
        builder.append("Schedule periodic: ").append(config.getScheduler().getPeriodicIntervalMinutes()).append(" min\n");
        builder.append("Overwrite existing: ").append(config.getStorage().getOverwriteExisting()).append('\n');
        builder.append("Validate free space: ").append(config.getStorage().getValidateFreeSpace()).append('\n');
        return builder.toString();
    }

    private String buildStoragePreview(DownloadConfig config) {
        StorageResolver resolver = new StorageResolver(getApplicationContext(), config.getStorage());
        DownloadRequest request = createSampleRequest();
        try {
            StorageResolution resolution = resolver.resolve(request, true);
            return "Next storage target:\n• Directory: " + resolution.getDirectory().getAbsolutePath() +
                    "\n• File: " + resolution.getFile().getAbsolutePath() +
                    "\n• Would overwrite existing: " + resolution.getOverwroteExisting();
        } catch (StorageResolutionException error) {
            return "Storage resolver error: " + error.getMessage();
        }
    }

    private ScheduleTime createExactScheduleTime() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 12);
        calendar.set(java.util.Calendar.MINUTE, 30);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return new ScheduleTime(
                calendar.get(java.util.Calendar.HOUR_OF_DAY),
                calendar.get(java.util.Calendar.MINUTE),
                null,
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
        );
    }

    private String describeSchedule(ScheduleTime scheduleTime) {
        String hour = String.format(Locale.US, "%02d", scheduleTime.getHour());
        String minute = String.format(Locale.US, "%02d", scheduleTime.getMinute());
        String datePart;
        if (scheduleTime.getYear() != null && scheduleTime.getMonth() != null && scheduleTime.getDayOfMonth() != null) {
            datePart = scheduleTime.getYear() + "-" +
                    String.format(Locale.US, "%02d", scheduleTime.getMonth()) + "-" +
                    String.format(Locale.US, "%02d", scheduleTime.getDayOfMonth());
        } else if (scheduleTime.getWeekday() != null) {
            datePart = prettifyWeekday(scheduleTime.getWeekday().name());
        } else {
            datePart = "Daily";
        }
        return datePart + " at " + hour + ":" + minute;
    }

    private String prettifyWeekday(String raw) {
        if (raw.isEmpty()) return raw;
        String lower = raw.toLowerCase(Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String formattedTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private String extractBaseName(String url) {
        String cleanUrl = url.split("\\?")[0];
        String raw = cleanUrl.substring(cleanUrl.lastIndexOf('/') + 1);
        return raw.isEmpty() ? "download.bin" : raw;
    }


    private String toHumanReadable(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[index]);
    }
    private void ensureStoragePermission(Runnable onGranted) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onGranted.run();
            return;
        }
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                pendingPermissionAction = onGranted;
                requestPermissions(REQUIRED_PERMISSIONS, REQUEST_STORAGE_PERMISSIONS);
                return;
            }
        }
        onGranted.run();
    }

    private static final int REQUEST_STORAGE_PERMISSIONS = 200;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
}

