package by.instruction.papera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class VibrometerGraphView extends View {
    private static final int MAX_SAMPLES = 120;
    private final float[] samples = new float[MAX_SAMPLES];
    private int count = 0;
    private int head = 0;

    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public VibrometerGraphView(Context context) {
        super(context);
        init();
    }

    public VibrometerGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VibrometerGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(2f));
        framePaint.setColor(Color.parseColor("#66FFC107"));

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(Color.parseColor("#33FFC107"));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.2f));
        linePaint.setColor(Color.parseColor("#FFFFC107"));

    }

    public void addSample(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return;
        }
        samples[head] = Math.max(0f, value);
        head = (head + 1) % MAX_SAMPLES;
        if (count < MAX_SAMPLES) {
            count++;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float pad = dp(8f);
        float left = pad;
        float top = pad;
        float right = w - pad;
        float bottom = h - pad;

        canvas.drawRect(left, top, right, bottom, framePaint);
        float midY = (top + bottom) / 2f;
        canvas.drawLine(left, midY, right, midY, gridPaint);

        if (count < 2) {
            return;
        }

        float max = 0f;
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + MAX_SAMPLES) % MAX_SAMPLES;
            max = Math.max(max, samples[idx]);
        }
        float scaleMax = Math.max(0.2f, max * 1.15f);
        float graphWidth = right - left;
        float graphHeight = bottom - top;

        float prevX = left;
        float prevY = bottom - (samples[(head - count + MAX_SAMPLES) % MAX_SAMPLES] / scaleMax) * graphHeight;
        for (int i = 1; i < count; i++) {
            int idx = (head - count + i + MAX_SAMPLES) % MAX_SAMPLES;
            float x = left + (graphWidth * i) / (count - 1f);
            float y = bottom - (samples[idx] / scaleMax) * graphHeight;
            canvas.drawLine(prevX, prevY, x, y, linePaint);
            prevX = x;
            prevY = y;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
