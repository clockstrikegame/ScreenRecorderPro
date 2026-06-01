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

public class FloatingWindowService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isMoving = false;

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingView = LayoutInflater.from(this)
                .inflate(R.layout.floating_controls, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 16;
        params.y = 300;

        windowManager.addView(floatingView, params);

        ImageButton btnStop = floatingView.findViewById(R.id.fab_stop);

        // السحب
        floatingView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isMoving = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getRawX() - initialTouchX);
                    float dy = Math.abs(event.getRawY() - initialTouchY);
                    if (dx > 5 || dy > 5) isMoving = true;
                    params.x = initialX - (int)(event.getRawX() - initialTouchX);
                    params.y = initialY + (int)(event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(floatingView, params);
                    return true;

                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        });

        btnStop.setOnClickListener(v -> {
            if (!isMoving) {
                Intent stopIntent = new Intent(this, RecordingService.class);
                stopIntent.setAction(RecordingService.ACTION_STOP);
                startService(stopIntent);
                stopSelf();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
