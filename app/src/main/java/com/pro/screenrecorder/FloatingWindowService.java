package com.pro.screenrecorder;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class FloatingWindowService extends Service {

    private WindowManager              wm;
    private View                       root;
    private WindowManager.LayoutParams params;

    private int     initX, initY;
    private float   initTX, initTY;
    private boolean moved;

    private Timer    timer;
    private long     startMs;
    private TextView tvTimer;

    @Override
    public void onCreate() {
        super.onCreate();

        wm   = (WindowManager) getSystemService(WINDOW_SERVICE);
        root = LayoutInflater.from(this)
                   .inflate(R.layout.floating_controls, null);

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 24;
        params.y = 260;

        wm.addView(root, params);

        tvTimer = root.findViewById(R.id.tv_timer);
        ImageButton btnStop = root.findViewById(R.id.fab_stop);

        root.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initX  = params.x;
                    initY  = params.y;
                    initTX = e.getRawX();
                    initTY = e.getRawY();
                    moved  = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - initTX;
                    float dy = e.getRawY() - initTY;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                    params.x = initX - (int) dx;
                    params.y = initY + (int) dy;
                    wm.updateViewLayout(root, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        });

        btnStop.setOnClickListener(v -> {
            if (!moved) {
                startService(
                    new Intent(this, RecordingService.class)
                        .setAction(RecordingService.ACTION_STOP));
                stopSelf();
            }
        });

        startMs = System.currentTimeMillis();
        timer   = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                long sec = (System.currentTimeMillis() - startMs) / 1000;
                String t = String.format(Locale.getDefault(),
                    "%02d:%02d", sec / 60, sec % 60);
                if (root != null) {
                    root.post(() -> {
                        if (tvTimer != null) tvTimer.setText(t);
                    });
                }
            }
        }, 0, 1000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (root != null && wm != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {}
            root = null;
        }
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
