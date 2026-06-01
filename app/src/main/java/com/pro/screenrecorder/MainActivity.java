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

    private static final int REQUEST_AUDIO_PERMISSION = 100;
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
    private boolean isRecording = false;

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
        checkNotificationPermission();
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
            if (!isRecording) startRecordingFlow();
            else stopRecording();
        });

        cardSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        cardFiles.setOnClickListener(v -> openRecordingsFolder());

        cardHelp.setOnClickListener(v -> showHelpDialog());
    }

    private void setupLaunchers() {
        screenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK
                            && result.getData() != null) {
                        startRecordingService(result.getResultCode(), result.getData());
                    } else {
                        Toast.makeText(this,
                                getString(R.string.permission_denied),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Settings.canDrawOverlays(this)) requestScreenCapture();
                });
    }

    private void startRecordingFlow() {
        if (!Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.overlay_permission_title)
                    .setMessage(R.string.overlay_permission_message)
                    .setPositiveButton(R.string.grant, (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        overlayPermissionLauncher.launch(i);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO_PERMISSION);
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO_PERMISSION);
            return;
        }

        requestScreenCapture();
    }

    private void requestScreenCapture() {
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent());
    }

    private void startRecordingService(int resultCode, Intent data) {
        Intent si = new Intent(this, RecordingService.class);
        si.setAction(RecordingService.ACTION_START);
        si.putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode);
        si.putExtra(RecordingService.EXTRA_DATA, data);
        si.putExtra(RecordingService.EXTRA_RESOLUTION, prefs.getString("resolution", "1080p"));
        si.putExtra(RecordingService.EXTRA_FPS,        prefs.getString("fps", "30"));
        si.putExtra(RecordingService.EXTRA_BITRATE,    prefs.getString("bitrate", "8000000"));
        si.putExtra(RecordingService.EXTRA_AUDIO,      prefs.getBoolean("record_audio", true));
        ContextCompat.startForegroundService(this, si);

        isRecording = true;
        updateRecordingUI(true);
        Toast.makeText(this, R.string.recording_started, Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    private void stopRecording() {
        Intent si = new Intent(this, RecordingService.class);
        si.setAction(RecordingService.ACTION_STOP);
        startService(si);
        isRecording = false;
        updateRecordingUI(false);
        Toast.makeText(this, R.string.recording_stopped, Toast.LENGTH_SHORT).show();
    }

    private void updateRecordingUI(boolean recording) {
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
        String resolution = prefs.getString("resolution", "1080p");
        String fps        = prefs.getString("fps", "30");
        String bitrateRaw = prefs.getString("bitrate", "8000000");

        tvResolution.setText(resolution);
        tvFps.setText(fps + " FPS");
        int bval = Integer.parseInt(bitrateRaw);
        tvBitrate.setText((bval / 1_000_000) + " Mbps");

        File path  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        long freeG = path.getFreeSpace() / (1024L * 1024 * 1024);
        tvStorage.setText(freeG + " GB " + getString(R.string.free));
    }

    private void openRecordingsFolder() {
        File folder = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "ScreenRecorderPro");
        Toast.makeText(this, folder.getAbsolutePath(), Toast.LENGTH_LONG).show();
    }

    private void showHelpDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 200);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatsDisplay();
        isRecording = RecordingService.isRunning;
        updateRecordingUI(isRecording);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQUEST_AUDIO_PERMISSION && res.length > 0
                && res[0] == PackageManager.PERMISSION_GRANTED) {
            requestScreenCapture();
        }
    }
}
