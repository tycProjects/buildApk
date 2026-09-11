package com.example.aimdragtrainer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.util.Random;

public class TrainerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    private float crossX, crossY;
    private float targetX, targetY;
    private float lastX, lastY;
    private float sensitivity = 1.0f;
    private boolean dragging;
    private boolean autoHeadLock;
    private boolean effectMode;
    private long lastFrame;
    private int score;
    private TextView scoreView;

    public TrainerView(Context context) {
        super(context);
    }

    public void setScoreView(TextView view) {
        scoreView = view;
    }

    public void setSensitivity(float value) {
        sensitivity = value;
    }

    public boolean isAutoHeadLock() {
        return autoHeadLock;
    }

    public void setAutoHeadLock(boolean enabled) {
        autoHeadLock = enabled;
        invalidate();
    }

    public void setEffectMode(boolean enabled) {
        effectMode = enabled;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        crossX = w / 2f;
        crossY = h * 0.72f;
        spawnTarget(w, h);
    }

    private void spawnTarget(int w, int h) {
        targetX = 70 + random.nextFloat() * Math.max(1, w - 140);
        targetY = 130 + random.nextFloat() * Math.max(1, h * 0.48f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(245, 245, 245));

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(Color.DKGRAY);
        canvas.drawCircle(targetX, targetY, 48, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(210, 60, 60));
        canvas.drawCircle(targetX, targetY, 20, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(crossX, crossY, 24, paint);
        canvas.drawLine(crossX - 36, crossY, crossX + 36, crossY, paint);
        canvas.drawLine(crossX, crossY - 36, crossX, crossY + 36, paint);

        if (autoHeadLock) {
            paint.setColor(Color.rgb(30, 160, 80));
            paint.setStrokeWidth(3);
            canvas.drawCircle(targetX, targetY, 58, paint);
        }
        if (effectMode) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.rgb(30, 200, 240));
            canvas.drawCircle(targetX, targetY, 70, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.GRAY);
        paint.setTextSize(28);
        canvas.drawText("Kéo tâm vào vùng đầu", 20, getHeight() - 25, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            lastX = x;
            lastY = y;
            dragging = true;
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE && dragging) {
            crossX += (x - lastX) * sensitivity;
            crossY += (y - lastY) * sensitivity;

            if (autoHeadLock) {
                crossX += (targetX - crossX) * 0.18f;
                crossY += (targetY - crossY) * 0.18f;
            }

            lastX = x;
            lastY = y;
            invalidate();
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            dragging = false;

            float distance = (float)Math.hypot(
                    crossX - targetX,
                    crossY - targetY
            );

            if (distance < 55) {
                score++;
                if (scoreView != null) {
                    scoreView.setText("Điểm: " + score);
                }
                spawnTarget(getWidth(), getHeight());
            }

            crossX = getWidth() / 2f;
            crossY = getHeight() * 0.72f;
            invalidate();
            return true;
        }

        return true;
    }
}
