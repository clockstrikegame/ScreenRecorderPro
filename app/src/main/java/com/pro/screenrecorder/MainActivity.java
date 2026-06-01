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

    private static final int REQ_PERMS = 100;

    private MediaProjectionManager projMgr;
    private ActivityResultLauncher<Intent> captureLauncher;
    private ActivityResultLauncher<Intent> overlayLauncher;

    private MaterialButton btnRecord;
    private TextView tvStatus, tvResolution, tvFps, tvBitrate, tvStorage;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs   = PreferenceManager.getDefaultSharedPreferences(this);
        projMgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        bindViews();
        setupLaunchers();
        askNotificationPerm();
    }

    private void bindViews() {
        btnRecord    = findViewById(R.id.btn_record);
        tvStatus     = findViewById(R.id.tv_status);
        tvResolution = findViewById(R.id.tv_resolution);
        tvFps        = findViewById(R.id.tv_fps);
        tvBitrate    = findViewById(R.id.tv_bitrate);
        tvStorage    = findViewById(R.id.tv_storage);

        btnRecord.setOnClickListener(v -> {
            if (RecordingService.isRunning) stopEverything();
            else startFlow();
        });

        MaterialCardView cSettings = findViewById(R.id.card_settings);
        MaterialCardView cFiles    = findViewById(R.id.card_files);
        MaterialCardView cHelp     = findViewById(R.id.card_help);

        cSettings.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));

        cFiles.setOnClickListener(v ->
            startActivity(new Intent(this, RecordingsActivity.class)));

        cHelp.setOnClickListener(v -> showHelp());
    }

    private void setupLaunchers() {
        captureLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            r -> {
                if (r.getResultCode() == Activity.RESULT_OK
                        && r.getData() != null) {
                    launchServices(r.getResultCode(), r.getData());
                } else {
                    toast(getString(R.string.permission_denied));
                }
            });

        overlayLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            r -> {
                if (Settings.canDrawOverlays(this)) {
                    checkPermsAndRecord();
                }
            });
    }

    private void startFlow() {
        if (!Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.overlay_permission_title)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton(R.string.grant, (d, w) ->
                    overlayLauncher.launch(new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))))
                .setNegativeButton(R.string.cancel, null)
                .show();
            return;
        }
        checkPermsAndRecord();
    }

    private void checkPermsAndRecord() {
        boolean noAudio = ContextCompat.checkSelfPermission(this,
            Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED;

        boolean noStorage = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
            && ContextCompat.checkSelfPermission(this,
               Manifest.permission.WRITE_EXTERNAL_STORAGE)
               != PackageManager.PERMISSION_GRANTED;

        if (noAudio || noStorage) {
            ActivityCompat.requestPermissions(this,
                noStorage
                    ? new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}
                    : new String[]{Manifest.permission.RECORD_AUDIO},
                REQ_PERMS);
        } else {
            captureLauncher.launch(projMgr.createScreenCaptureIntent());
        }
    }

    private void launchServices(int code, Intent data) {
        Intent si = new Intent(this, RecordingService.class)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_RESULT_CODE, code)
            .putExtra(RecordingService.EXTRA_DATA, data)
            .putExtra(RecordingService.EXTRA_RESOLUTION,
                prefs.getString("resolution", "1080p"))
            .putExtra(RecordingService.EXTRA_FPS,
                prefs.getString("fps", "30"))
            .putExtra(RecordingService.EXTRA_BITRATE,
                prefs.getString("bitrate", "8000000"))
            .putExtra(RecordingService.EXTRA_AUDIO,
                prefs.getBoolean("record_audio", true));

        ContextCompat.startForegroundService(this, si);
        startService(new Intent(this, FloatingWindowService.class));

        updateUI(true);
        moveTaskToBack(true);
    }

    private void stopEverything() {
        startService(new Intent(this, RecordingService.class)
            .setAction(RecordingService.ACTION_STOP));
        stopService(new Intent(this, FloatingWindowService.class));
        updateUI(false);
        toast(getString(R.string.recording_stopped));
    }

    private void updateUI(boolean recording) {
        if (recording) {
            btnRecord.setText(R.string.stop_recording);
            btnRecord.setIconResource(R.drawable.ic_stop);
            btnRecord.setBackgroundTintList(
                getColorStateList(R.color.accent_red));
            tvStatus.setText(R.string.status_recording);
            tvStatus.setTextColor(getColor(R.color.accent_red));
        } else {
            btnRecord.setText(R.string.start_recording);
            btnRecord.setIconResource(R.drawable.ic_record);
            btnRecord.setBackgroundTintList(
                getColorStateList(R.color.accent_purple));
            tvStatus.setText(R.string.status_ready);
            tvStatus.setTextColor(getColor(R.color.accent_green));
        }
        refreshStats();
    }

    private void refreshStats() {
        String res = prefs.getString("resolution", "1080p");
        String fps = prefs.getString("fps", "30");
        int    bps = Integer.parseInt(
            prefs.getString("bitrate", "8000000"));

        tvResolution.setText(res);
        tvFps.setText(fps + " FPS");
        tvBitrate.setText((bps / 1_000_000) + " Mbps");

        try {
            File p  = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES);
            long gb = p.getFreeSpace() / (1024L * 1024 * 1024);
            tvStorage.setText(gb + " GB " + getString(R.string.free));
        } catch (Exception e) {
            tvStorage.setText("-- GB");
        }
    }

    private void showHelp() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_message)
            .setPositiveButton(R.string.ok, null)
            .show();
    }

    private void askNotificationPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(this,
               Manifest.permission.POST_NOTIFICATIONS)
               != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                200);
        }
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI(RecordingService.isRunning);
    }

    @Override
    public void onRequestPermissionsResult(int req,
            String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == REQ_PERMS && r.length > 0
            && r[0] == PackageManager.PERMISSION_GRANTED) {
            captureLauncher.launch(projMgr.createScreenCaptureIntent());
        }
    }
}
