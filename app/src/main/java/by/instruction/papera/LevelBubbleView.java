package by.instruction.papera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class LevelBubbleView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float pitchDeg = 0f;
    private float rollDeg = 0f;

    public LevelBubbleView(Context context) {
        super(context);
        init();
    }

    public LevelBubbleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LevelBubbleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int accent = Color.parseColor("#FFC107");

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(3));
        ringPaint.setColor(accent);

        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(dp(1.5f));
        crossPaint.setColor(Color.parseColor("#88FFC107"));

        bubblePaint.setStyle(Paint.Style.FILL);
        bubblePaint.setColor(accent);

        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(Color.parseColor("#66FFC107"));
    }

    public void setTiltAngles(float pitch, float roll) {
        this.pitchDeg = pitch;
        this.rollDeg = roll;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) * 0.43f;
        float bubbleRadius = Math.max(dp(8), radius * 0.11f);
        float maxMove = radius - bubbleRadius - dp(4);

        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crossPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crossPaint);
        canvas.drawCircle(cx, cy, dp(4), centerPaint);

        // Typical phone-level range is within +/-30deg for useful surface checks.
        float normX = clamp(rollDeg / 30f, -1f, 1f);
        float normY = clamp(pitchDeg / 30f, -1f, 1f);

        float bx = cx + normX * maxMove;
        float by = cy + normY * maxMove;
        canvas.drawCircle(bx, by, bubbleRadius, bubblePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
