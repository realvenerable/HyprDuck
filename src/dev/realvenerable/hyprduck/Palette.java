package dev.realvenerable.hyprduck;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;

/**
 * Resolved colour set and shape helpers.
 *
 * Every colour the UI uses comes from the theme, never from a hardcoded
 * constant: an earlier build used Color.DKGRAY and Color.GRAY, which is why the
 * secondary text was unreadable on a dark theme.
 */
final class Palette {

    final int accent;
    final int onAccent;
    final int textPrimary;
    final int textSecondary;
    final int surface;
    final int card;
    final int cardStroke;
    final int track;
    final int segmentSelected;

    private final float density;

    Palette(Context context) {
        accent = resolve(context, R.attr.blAccent, 0xFFF2C94C);
        onAccent = resolve(context, R.attr.blOnAccent, 0xFF0B0B0D);
        textPrimary = resolve(context, R.attr.blTextPrimary, 0xFFF5F5F7);
        textSecondary = resolve(context, R.attr.blTextSecondary, 0xFF8A8A8F);
        surface = resolve(context, R.attr.blSurface, 0xFF0B0B0D);
        card = resolve(context, R.attr.blCard, 0xFF1A1A1D);
        cardStroke = resolve(context, R.attr.blCardStroke, 0xFF2C2C31);
        track = resolve(context, R.attr.blTrack, 0xFF26262A);
        segmentSelected = resolve(context, R.attr.blSegmentSelected, 0xFF33333A);
        density = context.getResources().getDisplayMetrics().density;
    }

    /**
     * A theme attribute may resolve to a literal colour or to a reference to a
     * colour resource, so both shapes are handled.
     */
    private static int resolve(Context context, int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            return fallback;
        }
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        if (value.resourceId != 0) {
            try {
                return context.getResources().getColor(value.resourceId, context.getTheme());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    int dp(float value) {
        return Math.round(value * density);
    }

    /** Same hue at reduced opacity. */
    static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color),
                Color.green(color), Color.blue(color));
    }

    /**
     * Wraps a drawable in a ripple tinted to the accent, which is the touch
     * feedback used throughout the reference set.
     */
    static Drawable ripple(int rippleColor, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    /** Elevated card: lighter than the surface, hairline border, large radius. */
    GradientDrawable card(int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(card);
        d.setStroke(Math.max(1, dp(1)), cardStroke);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    /** Fully rounded chip or button. */
    GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(999));
        return d;
    }

    /** Transparent fill with a hairline border, for secondary actions. */
    GradientDrawable outlined(float radiusDp, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(Color.TRANSPARENT);
        d.setStroke(Math.max(1, dp(strokeDp)), cardStroke);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }
}
