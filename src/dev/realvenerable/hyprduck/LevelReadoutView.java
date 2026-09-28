package dev.realvenerable.hyprduck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

/**
 * The big level number, centred in the space it is given, e.g. "142".
 *
 * Drawn rather than composed from a TextView on purpose. The number uses a light
 * weight at a large size, the way the reference set sets its numerals, and it has
 * to shrink to fit the available width at any font scale. Auto-sizing a TextView
 * cannot do that here: inside a horizontal wrap_content row the view is measured
 * to fit its own text, so it never shrinks and clips instead, which is what the
 * first version did.
 *
 * The size is fitted against the widest value the ramp can hold, not against the
 * value on screen, which is what makes the numeral steady: it never resizes and
 * never slides sideways as the level changes. Its unit is not drawn here at all
 * either, being a static label in the corner, so the level is the only thing on
 * the card that moves.
 */
final class LevelReadoutView extends View {

    /** "255" is the widest the 0-255 ramp gets, so it is what sets the size. */
    private static final String REFERENCE = "255";

    /**
     * The share of the width the numeral is fitted into. It keeps a margin in
     * hand so the numeral never runs into the card's own padding or under its
     * rounded corners.
     */
    private static final float WIDTH_SHARE = 0.88f;

    private final TextPaint numberPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

    private String number = "--";
    private float minSizePx;
    private float maxSizePx;
    private float stepPx;

    LevelReadoutView(Context context) {
        super(context);

        numberPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        minSizePx = spToPx(48f, dm);
        maxSizePx = spToPx(210f, dm);
        stepPx = dp(1f, dm);
    }

    void setColours(int number) {
        numberPaint.setColor(number);
        invalidate();
    }

    void setValue(String value) {
        if (value.equals(number)) {
            return;
        }
        number = value;
        // No requestLayout: the size was fitted against a value that does not
        // change, so a new level cannot move or resize anything.
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
                // 0.80 of the height for a font whose drawn box is about 1.17
                // times its size, so the box keeps about a twentieth of the area
                // in hand. That is what leaves the corner unit, and the meter
                // below it, clear of the numeral. Higher than this looks right
                // on a tall screen and crowds the bottom on a short one.
                size = Math.min(size, availableHeight * 0.80f);
            }
        }

        // Shrink until the widest value fits, never the current one, so the size
        // holds steady from 0 to 255.
        float budget = available * WIDTH_SHARE;
        numberPaint.setTextSize(size);
        while (size > minSizePx && numberPaint.measureText(REFERENCE) > budget) {
            size -= stepPx;
            numberPaint.setTextSize(size);
        }

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

        int left = getPaddingLeft();
        int right = getWidth() - getPaddingRight();
        int top = getPaddingTop();
        int bottom = getHeight() - getPaddingBottom();

        // Centred both ways, so the level is read from the same spot whatever it
        // happens to be. The baseline is placed off the centre of the glyph box
        // rather than the baseline of the font, which is what makes it look
        // centred rather than sitting too high.
        float x = left + (right - left - numberPaint.measureText(number)) / 2f;
        float y = top + (bottom - top - (fm.descent - fm.ascent)) / 2f - fm.ascent;

        canvas.drawText(number, x, y, numberPaint);
    }

    private static float spToPx(float sp, DisplayMetrics dm) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm);
    }

    private float dp(float value, DisplayMetrics dm) {
        return value * dm.density;
    }
}
