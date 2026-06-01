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

    public static final String ACTION_START      = "ACTION_START";
    public static final String ACTION_STOP       = "ACTION_STOP";
    public static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA        = "EXTRA_DATA";
    public static final String EXTRA_RESOLUTION  = "EXTRA_RESOLUTION";
    public static final String EXTRA_FPS         = "EXTRA_FPS";
    public static final String EXTRA_BITRATE     = "EXTRA_BITRATE";
    public static final String EXTRA_AUDIO       = "EXTRA_AUDIO";

    private static final String CHANNEL_ID      = "RecordingChannel";
    private static final int    NOTIFICATION_ID  = 1;

    public static volatile boolean isRunning = false;

    private MediaProjection mediaProjection;
    private MediaRecorder   mediaRecorder;
    private VirtualDisplay  virtualDisplay;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_START.equals(intent.getAction())) {
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
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
        }
        return START_STICKY;
    }

    private void startRecording(int code, Intent data,
            String res, String fps, String bitrate, boolean audio) {

        MediaProjectionManager pm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = pm.getMediaProjection(code, data);

        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(m);

        int[] d    = dims(res, m);
        int   w    = d[0], h = d[1];
        int   den  = m.densityDpi;
        int   fpsV = Integer.parseInt(fps  == null ? "30"       : fps);
        int   bpsV = Integer.parseInt(bitrate == null ? "8000000" : bitrate);
        String out = outputPath();

        mediaRecorder = new MediaRecorder();
        if (audio) mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (audio) {
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);
        }
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoSize(w, h);
        mediaRecorder.setVideoFrameRate(fpsV);
        mediaRecorder.setVideoEncodingBitRate(bpsV);
        mediaRecorder.setOutputFile(out);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "SRP", w, h, den,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(), null, null);
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
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception ignored) {}

        if (virtualDisplay  != null) { virtualDisplay.release();  virtualDisplay  = null; }
        if (mediaProjection != null) { mediaProjection.stop();    mediaProjection = null; }

        stopForeground(true);
        stopSelf();
    }

    private int[] dims(String r, DisplayMetrics m) {
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

    private String outputPath() {
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES), "ScreenRecorderPro");
        if (!dir.exists()) dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        return new File(dir, "REC_" + ts + ".mp4").getAbsolutePath();
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, RecordingService.class)
                        .setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_recording))
                .setSmallIcon(R.drawable.ic_record)
                .setContentIntent(open)
                .addAction(R.drawable.ic_stop,
                        getString(R.string.stop_recording), stop)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class)
                .createNotificationChannel(ch);
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
