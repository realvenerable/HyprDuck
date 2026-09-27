package dev.realvenerable.hyprduck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Pill slider: hairline track, accent fill, round thumb.
 *
 * Custom rather than a platform SeekBar so it matches the tick meter, and so the
 * poller can leave the thumb alone while the user is dragging.
 */
final class SliderView extends View {

    interface OnValueChanged {
        /** @param fromUser true while the finger is down, false on a sync. */
        void onValueChanged(int value, boolean fromUser);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnValueChanged listener;
    private int value;
    private boolean dragging;
    private float trackHeight;
    private float thumbRadius;

    SliderView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        trackHeight = dp(4f);
        thumbRadius = dp(10f);
        trackPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.FILL);
    }

    void setColours(int accent, int thumb, int surface, int track) {
        trackPaint.setColor(track);
        fillPaint.setColor(accent);
        thumbPaint.setColor(thumb);
        // A ring in the surface colour reads as a soft edge over the card.
        ringPaint.setColor(surface);
        invalidate();
    }

    void setOnValueChangedListener(OnValueChanged l) {
        this.listener = l;
    }

    void setValue(int v) {
        int clamped = clamp(v);
        if (clamped == value) {
            return;
        }
        value = clamped;
        invalidate();
    }

    int getValue() {
        return value;
    }

    /** True while the finger is down, so the poller can leave the thumb alone. */
    boolean isDragging() {
        return dragging;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(
                resolveSize((int) dp(200f), widthSpec),
                (int) dp(40f));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Keep the parent ScrollView from stealing the drag.
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                updateFromX(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    updateFromX(event.getX());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateFromX(float x) {
        int usable = getWidth() - Math.round(thumbRadius * 2f);
        if (usable <= 0) {
            return;
        }
        float fraction = (x - thumbRadius) / usable;
        fraction = Math.max(0f, Math.min(1f, fraction));
        int next = clamp(Math.round(fraction * 255f));
        if (next != value) {
            value = next;
            invalidate();
            if (listener != null) {
                listener.onValueChanged(next, true);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        float cy = getHeight() / 2f;
        float left = thumbRadius;
        float right = w - thumbRadius;
        float half = trackHeight / 2f;

        canvas.drawRoundRect(left, cy - half, right, cy + half, half, half, trackPaint);

        float fraction = value / 255f;
        if (fraction > 0f) {
            canvas.drawRoundRect(left, cy - half, left + (right - left) * fraction,
                    cy + half, half, half, fillPaint);
        }

        float thumbX = left + (right - left) * fraction;
        // Ring first, thumb on top, for a clean edge against the card.
        canvas.drawCircle(thumbX, cy, thumbRadius + dp(2f), ringPaint);
        canvas.drawCircle(thumbX, cy, thumbRadius, thumbPaint);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
