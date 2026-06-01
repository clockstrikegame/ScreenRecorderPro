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
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {

    private static final String TAG = "RecordingService";

    public static final String ACTION_START      = "ACTION_START";
    public static final String ACTION_STOP       = "ACTION_STOP";
    public static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA        = "EXTRA_DATA";
    public static final String EXTRA_RESOLUTION  = "EXTRA_RESOLUTION";
    public static final String EXTRA_FPS         = "EXTRA_FPS";
    public static final String EXTRA_BITRATE     = "EXTRA_BITRATE";
    public static final String EXTRA_AUDIO       = "EXTRA_AUDIO";

    public static final String ACTION_RECORDING_STOPPED = 
        "com.pro.screenrecorder.RECORDING_STOPPED";

    private static final String CHANNEL_ID     = "RecordingChannel";
    private static final int    NOTIFICATION_ID = 1;

    public static volatile boolean isRunning   = false;
    public static volatile String  lastSavedFile = null;

    private MediaProjection mediaProjection;
    private MediaRecorder   mediaRecorder;
    private VirtualDisplay  virtualDisplay;
    private String          outputFilePath;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        switch (intent.getAction() == null ? "" : intent.getAction()) {
            case ACTION_START:
                createChannel();
                startForeground(NOTIFICATION_ID, buildNotification());
                startRecording(
                    intent.getIntExtra(EXTRA_RESULT_CODE, -1),
                    intent.getParcelableExtra(EXTRA_DATA),
                    intent.getStringExtra(EXTRA_RESOLUTION),
                    intent.getStringExtra(EXTRA_FPS),
                    intent.getStringExtra(EXTRA_BITRATE),
                    intent.getBooleanExtra(EXTRA_AUDIO, true)
                );
                break;
            case ACTION_STOP:
                stopRecording();
                break;
        }
        return START_NOT_STICKY;
    }

    private void startRecording(int code, Intent data,
            String res, String fps, String bitrate, boolean audio) {

        try {
            MediaProjectionManager pm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = pm.getMediaProjection(code, data);

            DisplayMetrics metrics = new DisplayMetrics();
            ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(metrics);

            int[] d   = resolutionDims(res, metrics);
            int   w   = d[0];
            int   h   = d[1];
            int   den = metrics.densityDpi;
            int   f   = safeFps(fps);
            int   b   = safeBitrate(bitrate);

            outputFilePath = buildOutputPath();
            lastSavedFile  = outputFilePath;

            // ← الترتيب الصحيح لـ MediaRecorder
            mediaRecorder = new MediaRecorder();

            if (audio) {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(outputFilePath);
            mediaRecorder.setVideoSize(w, h);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(b);
            mediaRecorder.setVideoFrameRate(f);
            if (audio) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioSamplingRate(44100);
                mediaRecorder.setAudioEncodingBitRate(192000);
            }

            mediaRecorder.prepare();

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenRecorderPro",
                w, h, den,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(),
                null, null
            );

            mediaRecorder.start();
            isRunning = true;
            Log.d(TAG, "Recording started → " + outputFilePath);

        } catch (IOException e) {
            Log.e(TAG, "prepare() failed: " + e.getMessage());
            cleanup();
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "startRecording error: " + e.getMessage());
            cleanup();
            stopSelf();
        }
    }

    private void stopRecording() {
        isRunning = false;
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "stop error: " + e.getMessage());
        }

        cleanup();

        // أخبر المستمعين
        sendBroadcast(new Intent(ACTION_RECORDING_STOPPED));

        stopForeground(true);
        stopSelf();
    }

    private void cleanup() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    // ← الترتيب الصحيح الوحيد المعتمد
    private int[] resolutionDims(String r, DisplayMetrics m) {
        if (r == null) return new int[]{m.widthPixels, m.heightPixels};
        switch (r) {
            case "480p":  return new int[]{854,  480};
            case "720p":  return new int[]{1280, 720};
            case "1080p": return new int[]{1920, 1080};
            case "1440p": return new int[]{2560, 1440};
            case "4K":    return new int[]{3840, 2160};
            default:      return new int[]{m.widthPixels, m.heightPixels};
        }
    }

    private int safeFps(String fps) {
        try { return Integer.parseInt(fps); }
        catch (Exception e) { return 30; }
    }

    private int safeBitrate(String b) {
        try { return Integer.parseInt(b); }
        catch (Exception e) { return 8_000_000; }
    }

    private String buildOutputPath() {
        File dir = new File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES),
            "ScreenRecorderPro");
        if (!dir.exists()) dir.mkdirs();
        String ts = new SimpleDateFormat(
            "yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, "REC_" + ts + ".mp4").getAbsolutePath();
    }

    private Notification buildNotification() {
        PendingIntent openApp = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent stopRec = PendingIntent.getService(this, 1,
            new Intent(this, RecordingService.class)
                .setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("● Recording")
            .setContentText("Tap to open · Tap Stop to finish")
            .setSmallIcon(R.drawable.ic_record)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_stop, "Stop", stopRec)
            .setOngoing(true)
            .setColor(0xFFFF5252)
            .build();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "Screen Recording",
            NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
