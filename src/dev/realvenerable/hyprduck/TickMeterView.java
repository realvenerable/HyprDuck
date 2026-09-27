package dev.realvenerable.hyprduck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Row of thin vertical ticks, lit up to the current level.
 *
 * This is the recurring meter motif in the reference set (the arc of ticks in
 * ui0, the tick row in ui5, the rotation ticks in ui4). It reads as a level
 * meter rather than a progress bar, which suits a 0-255 ramp better.
 */
final class TickMeterView extends View {

    private static final int TICKS = 33;
    private static final int UNKNOWN = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int level = UNKNOWN;
    private int accent;
    private int dim;
    private int lit;
    private float tickWidth;
    private float tickHeight;

    TickMeterView(Context context) {
        super(context);
        setWillNotDraw(false);
        paint.setStyle(Paint.Style.FILL);
        tickWidth = dp(2f);
        tickHeight = dp(22f);
    }

    /**
     * @param dim    unlit ticks
     * @param lit    ticks up to the current level
     * @param accent the single tick at the current value
     */
    void setColours(int accent, int dim, int lit) {
        this.accent = accent;
        this.dim = dim;
        this.lit = lit;
        invalidate();
    }

    void setLevel(int value) {
        if (value == level) {
            return;
        }
        level = value;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(
                resolveSize((int) dp(220f), widthSpec),
                (int) dp(28f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        if (w <= 0) {
            return;
        }
        float centreY = getHeight() / 2f;
        float step = (w - tickWidth) / (TICKS - 1);
        float litThrough = level < 0 ? -1f : (level / 255f) * (TICKS - 1);
        int currentIndex = Math.round(litThrough);

        for (int i = 0; i < TICKS; i++) {
            float x = i * step;
            if (level >= 0 && i <= litThrough) {
                // The tick under the current value is the accent one.
                paint.setColor(i == currentIndex ? accent : lit);
            } else {
                paint.setColor(dim);
            }
            canvas.drawRoundRect(x, centreY - tickHeight / 2f,
                    x + tickWidth, centreY + tickHeight / 2f,
                    tickWidth, tickWidth, paint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
