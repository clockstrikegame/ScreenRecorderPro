package com.pro.screenrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;

    private MediaProjectionManager projectionManager;
    private ActivityResultLauncher<Intent> screenCaptureLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    private MaterialButton btnRecord;
    private TextView tvStatus;
    private TextView tvResolution;
    private TextView tvBitrate;
    private TextView tvFps;
    private TextView tvStorage;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        projectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        initViews();
        setupLaunchers();
        updateStatsDisplay();
        requestNotificationPermission();
    }

    private void initViews() {
        btnRecord    = findViewById(R.id.btn_record);
        tvStatus     = findViewById(R.id.tv_status);
        tvResolution = findViewById(R.id.tv_resolution);
        tvFps        = findViewById(R.id.tv_fps);
        tvBitrate    = findViewById(R.id.tv_bitrate);
        tvStorage    = findViewById(R.id.tv_storage);

        MaterialCardView cardSettings = findViewById(R.id.card_settings);
        MaterialCardView cardFiles    = findViewById(R.id.card_files);
        MaterialCardView cardHelp     = findViewById(R.id.card_help);

        btnRecord.setOnClickListener(v -> {
            if (!RecordingService.isRunning) {
                startRecordingFlow();
            } else {
                stopRecording();
            }
        });

        cardSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        cardFiles.setOnClickListener(v -> openFolder());
        cardHelp.setOnClickListener(v -> showHelp());
    }

    private void setupLaunchers() {
        screenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK
                            && result.getData() != null) {
                        launchRecordingService(
                                result.getResultCode(), result.getData());
                    } else {
                        toast(getString(R.string.permission_denied));
                    }
                });

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Settings.canDrawOverlays(this)) {
                        checkAudioAndRecord();
                    }
                });
    }

    private void startRecordingFlow() {
        if (!Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.overlay_permission_title)
                    .setMessage(R.string.overlay_permission_message)
                    .setPositiveButton(R.string.grant, (d, w) -> {
                        overlayPermissionLauncher.launch(
                                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + getPackageName())));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        checkAudioAndRecord();
    }

    private void checkAudioAndRecord() {
        boolean needAudio = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;

        boolean needStorage = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED;

        if (needAudio || needStorage) {
            String[] perms = needStorage
                    ? new String[]{Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}
                    : new String[]{Manifest.permission.RECORD_AUDIO};
            ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
        } else {
            requestScreenCapture();
        }
    }

    private void requestScreenCapture() {
        screenCaptureLauncher.launch(
                projectionManager.createScreenCaptureIntent());
    }

    private void launchRecordingService(int resultCode, Intent data) {
        // شغّل خدمة التسجيل
        Intent si = new Intent(this, RecordingService.class);
        si.setAction(RecordingService.ACTION_START);
        si.putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode);
        si.putExtra(RecordingService.EXTRA_DATA, data);
        si.putExtra(RecordingService.EXTRA_RESOLUTION,
                prefs.getString("resolution", "1080p"));
        si.putExtra(RecordingService.EXTRA_FPS,
                prefs.getString("fps", "30"));
        si.putExtra(RecordingService.EXTRA_BITRATE,
                prefs.getString("bitrate", "8000000"));
        si.putExtra(RecordingService.EXTRA_AUDIO,
                prefs.getBoolean("record_audio", true));
        ContextCompat.startForegroundService(this, si);

        // شغّل الزر العائم
        Intent fi = new Intent(this, FloatingWindowService.class);
        startService(fi);

        // أخفِ التطبيق فوراً
        moveTaskToBack(true);

        toast(getString(R.string.recording_started));
    }

    private void stopRecording() {
        Intent si = new Intent(this, RecordingService.class);
        si.setAction(RecordingService.ACTION_STOP);
        startService(si);

        stopService(new Intent(this, FloatingWindowService.class));
        toast(getString(R.string.recording_stopped));
        updateRecordingUI(false);
    }

    void updateRecordingUI(boolean recording) {
        if (recording) {
            btnRecord.setText(R.string.stop_recording);
            btnRecord.setIconResource(R.drawable.ic_stop);
            tvStatus.setText(R.string.status_recording);
            tvStatus.setTextColor(getColor(R.color.accent_red));
        } else {
            btnRecord.setText(R.string.start_recording);
            btnRecord.setIconResource(R.drawable.ic_record);
            tvStatus.setText(R.string.status_ready);
            tvStatus.setTextColor(getColor(R.color.accent_green));
        }
    }

    private void updateStatsDisplay() {
        String res     = prefs.getString("resolution", "1080p");
        String fps     = prefs.getString("fps", "30");
        int    bitrate = Integer.parseInt(
                prefs.getString("bitrate", "8000000"));

        tvResolution.setText(res);
        tvFps.setText(fps + " FPS");
        tvBitrate.setText((bitrate / 1_000_000) + " Mbps");

        File path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES);
        long freeGb = path.getFreeSpace() / (1024L * 1024 * 1024);
        tvStorage.setText(freeGb + " GB " + getString(R.string.free));
    }

    private void openFolder() {
        File f = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES), "ScreenRecorderPro");
        toast(f.getAbsolutePath());
    }

    private void showHelp() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        200);
            }
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatsDisplay();
        updateRecordingUI(RecordingService.isRunning);
    }

    @Override
    public void onRequestPermissionsResult(int req,
            String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQUEST_PERMISSIONS && res.length > 0
                && res[0] == PackageManager.PERMISSION_GRANTED) {
            requestScreenCapture();
        }
    }
}
