package dev.realvenerable.hyprduck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

/**
 * The big level number with a small unit beside it, e.g. "142  of 255".
 *
 * Drawn rather than composed from two TextViews on purpose. The number uses a
 * light weight at a large size, the way the reference set sets its numerals, and
 * it has to shrink to fit the available width at any font scale. Auto-sizing a
 * TextView cannot do that here: inside a horizontal wrap_content row the view is
 * measured to fit its own text, so it never shrinks and clips instead, which is
 * what the first version did.
 */
final class LevelReadoutView extends View {

    private final TextPaint numberPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint unitPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

    private String number = "--";
    private String unit = "";
    private float unitGap;
    private float minSizePx;
    private float maxSizePx;
    private float numberWidth;
    private float stepPx;

    LevelReadoutView(Context context) {
        super(context);

        numberPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        unitPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        unitPaint.setTextSize(spToPx(14f, dm));
        minSizePx = spToPx(40f, dm);
        maxSizePx = spToPx(150f, dm);
        stepPx = dp(1f, dm);
        unitGap = dp(8f, dm);
    }

    void setColours(int number, int unit) {
        numberPaint.setColor(number);
        unitPaint.setColor(unit);
        invalidate();
    }

    void setValue(String value) {
        if (value.equals(number)) {
            return;
        }
        number = value;
        requestLayout();
        invalidate();
    }

    void setUnit(String value) {
        if (value.equals(unit)) {
            return;
        }
        unit = value;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        if (available <= 0) {
            available = (int) dp(200f, getResources().getDisplayMetrics());
        }

        // Scale to the space actually given, so the numeral fills the hero area
        // on a tall screen instead of floating in it, and shrinks on a short one.
        float size = maxSizePx;
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            int availableHeight = MeasureSpec.getSize(heightSpec)
                    - getPaddingTop() - getPaddingBottom();
            if (availableHeight > 0) {
                // 0.66 rather than filling the hero area: the level card also
                // carries the meter and two labels below the numeral.
                size = Math.min(size, availableHeight * 0.66f);
            }
        }

        float unitWidth = unit.isEmpty() ? 0f : unitPaint.measureText(unit) + unitGap;

        // Shrink further until the number and its unit both fit the width.
        numberPaint.setTextSize(size);
        while (size > minSizePx
                && numberPaint.measureText(number) + unitWidth > available) {
            size -= stepPx;
            numberPaint.setTextSize(size);
        }
        numberWidth = numberPaint.measureText(number);

        TextPaint.FontMetrics fm = numberPaint.getFontMetrics();
        int height = (int) Math.ceil(fm.descent - fm.ascent);
        int target = Math.max(height, (int) dp(48f, getResources().getDisplayMetrics()));

        setMeasuredDimension(
                resolveSize(available + getPaddingLeft() + getPaddingRight(), widthSpec),
                resolveSize(target, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        TextPaint.FontMetrics fm = numberPaint.getFontMetrics();
        // Shared baseline, so the unit sits on the number's foot.
        float baseline = getPaddingTop() - fm.ascent;

        canvas.drawText(number, getPaddingLeft(), baseline, numberPaint);
        if (!unit.isEmpty()) {
            canvas.drawText(unit, getPaddingLeft() + numberWidth + unitGap, baseline, unitPaint);
        }
    }

    private static float spToPx(float sp, DisplayMetrics dm) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm);
    }

    private float dp(float value, DisplayMetrics dm) {
        return value * dm.density;
    }
}
