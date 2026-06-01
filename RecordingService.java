package com.pro.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA = "EXTRA_DATA";
    public static final String EXTRA_RESOLUTION = "EXTRA_RESOLUTION";
    public static final String EXTRA_FPS = "EXTRA_FPS";
    public static final String EXTRA_BITRATE = "EXTRA_BITRATE";
    public static final String EXTRA_AUDIO = "EXTRA_AUDIO";

    private static final String CHANNEL_ID = "RecordingChannel";
    private static final int NOTIFICATION_ID = 1;

    public static boolean isRunning = false;

    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private String outputFilePath;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_START.equals(intent.getAction())) {
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, buildNotification());

            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            String resolution = intent.getStringExtra(EXTRA_RESOLUTION);
            String fps = intent.getStringExtra(EXTRA_FPS);
            String bitrate = intent.getStringExtra(EXTRA_BITRATE);
            boolean audio = intent.getBooleanExtra(EXTRA_AUDIO, true);

            startRecording(resultCode, data, resolution, fps, bitrate, audio);

        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
        }

        return START_STICKY;
    }

    private void startRecording(int resultCode, Intent data,
            String resolution, String fps, String bitrate, boolean audio) {

        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);

        int[] dims = getResolutionDimensions(resolution, metrics);
        int width = dims[0];
        int height = dims[1];
        int density = metrics.densityDpi;
        int fpsVal = Integer.parseInt(fps);
        int bitrateVal = Integer.parseInt(bitrate);

        outputFilePath = getOutputFilePath();

        mediaRecorder = new MediaRecorder();

        if (audio) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        if (audio) {
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);
        }

        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoFrameRate(fpsVal);
        mediaRecorder.setVideoEncodingBitRate(bitrateVal);
        mediaRecorder.setOutputFile(outputFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenRecorder",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(),
                    null, null
            );

            isRunning = true;

        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    private void stopRecording() {
        isRunning = false;

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        stopForeground(true);
        stopSelf();
    }

    private int[] getResolutionDimensions(String resolution, DisplayMetrics metrics) {
        switch (resolution) {
            case "480p":  return new int[]{854, 480};
            case "720p":  return new int[]{1280, 720};
            case "1080p": return new int[]{1920, 1080};
            case "1440p": return new int[]{2560, 1440};
            case "4K":    return new int[]{3840, 2160};
            case "Native":
            default:
                return new int[]{metrics.widthPixels, metrics.heightPixels};
        }
    }

    private String getOutputFilePath() {
        File folder = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES),
                "ScreenRecorderPro");
        if (!folder.exists()) folder.mkdirs();

        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(folder, "REC_" + timestamp + ".mp4").getAbsolutePath();
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_recording))
                .setSmallIcon(R.drawable.ic_record)
                .setContentIntent(openPending)
                .addAction(R.drawable.ic_stop,
                        getString(R.string.stop_recording), stopPending)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
