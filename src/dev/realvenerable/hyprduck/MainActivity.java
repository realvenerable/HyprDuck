package dev.realvenerable.hyprduck;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * HyprDuck.
 *
 * A brightness level readout with optional adjustment, plus an About page,
 * reached from a bottom pill navigation bar.
 *
 * Both pages fill the screen rather than overflowing it. Every page column ends
 * with a fixed spacer so the last card never sits flush against the nav pill,
 * and the level card on the first page takes whatever space the cards below it
 * leave, which is what keeps that page filling the screen on a tall device.
 *
 * Filling it is not free: those cards below are fixed, so on a short screen they
 * can take all of the viewport and leave the level card nothing, and a weight
 * only ever adds space, never takes it back. So the page's spacing and card
 * padding are scaled down as the viewport shortens - see {@link #chromeScale()} -
 * and the numeral is bounded when it is measured with no height at all, which
 * is what the page's ScrollView does on its first pass. The ScrollView stays as
 * the fallback, and its scrollbars are disabled, so no scroll indicator is ever
 * shown: at a large font scale the first page is a short scroll rather than a
 * fitted page, and the second page, which has no card to absorb the slack, is
 * always a scroll.
 *
 * Permissions: WRITE_SETTINGS only, and only for the slider and the level
 * chips. Reading SCREEN_BRIGHTNESS and SCREEN_BRIGHTNESS_MODE needs no
 * permission, so the readout works even with it denied.
 *
 * Not shown: measured brightness in nits. android.view.Display has no
 * brightness method at all, and neither Display.BrightnessInfo nor
 * android.hardware.display.BrightnessInfo exists in the public SDK; both are
 * @SystemApi and blocked by non-SDK interface restrictions. Only real values
 * are shown rather than guessed ones, which is the same rule the device
 * temperature follows: see Thermals.
 */
public final class MainActivity extends Activity {

    private static final long POLL_MS = 500L;
    private static final int MAX_LEVEL = 255;
    private static final int MAX_PRESETS = 8;
    /** Tenths of the 0-255 ramp, i.e. 25, 51, 76, 102, ... 255. */
    private static final int QUICK_STEPS = 10;
    private static final int MIN_SDK = 26;
    private static final int TARGET_SDK = 35;
    private static final String GITHUB_URL = "https://github.com/realvenerable";

    private static final String PREFS_NAME = "hyprduck_state";
    private static final String KEY_PRESETS = "presets";

    /** Breathing room under the last card, above the nav pill. */
    private static final int BOTTOM_GAP_DP = 16;

    /**
     * The chrome scale, and the viewport it is derived from.
     *
     * The level card takes whatever space the cards below it leave, and those
     * cards are fixed, so on a short screen they can take all of it and the
     * level card is left with nothing. The spacing and the card padding are
     * what give way instead: below {@link #CHROME_ROOM} the page starts
     * tightening up, reaching {@link #CHROME_MIN} at
     * {@code CHROME_ROOM + CHROME_SPAN} and staying at 1 above that. Measured
     * against the three screens this was checked on, the page fits at 1 on the
     * 1079dp one and needs the tightening on the 834dp and 806dp ones, which
     * is what these numbers reproduce.
     *
     * ROOM and the viewport are dp, and the viewport is estimated from the
     * metrics because the window insets are not known until the view is
     * attached. The estimate is the screen less the root padding, the nav pill
     * and a nominal status and navigation bar, which runs about 25dp under the
     * real figure - close enough to decide how tight to be.
     */
    private static final float CHROME_ROOM = 620f;
    private static final float CHROME_SPAN = 300f;
    private static final float CHROME_MIN = 0.78f;
    private static final float WINDOW_ALLOWANCE_DP = 164f;

    // Adding a page: give it a constant, add a case in buildPage(), and add its
    // id, icon and label to NAV_PAGES, NAV_ICONS and NAV_LABELS. Nothing else.
    private static final int PAGE_BRIGHTNESS = 0;
    private static final int PAGE_ABOUT = 1;

    /** Left to right in the pill. */
    private static final int[] NAV_PAGES = {PAGE_BRIGHTNESS, PAGE_ABOUT};
    private static final int[] NAV_ICONS = {
            R.drawable.ic_nav_brightness, R.drawable.ic_nav_about};
    private static final int[] NAV_LABELS = {R.string.nav_brightness, R.string.nav_about};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashSet<Integer> presets = new LinkedHashSet<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, POLL_MS);
        }
    };

    private Palette palette;
    private SharedPreferences prefs;
    private Thermals thermals;
    private float chrome;

    private FrameLayout pageHost;
    private View[] pageViews;
    private LinearLayout[] navItems;
    private int currentPage = PAGE_BRIGHTNESS;

    // brightness page
    private LevelReadoutView readoutView;
    private TextView rampLabel;
    private TextView modeNote;
    private TextView floatLabel;
    private TextView tempLabel;
    private TextView savedEmpty;
    private TextView saveButton;
    private TextView grantButton;
    private TextView hintLabel;
    private TextView manualSegment;
    private TextView autoSegment;
    private TickMeterView meter;
    private SliderView slider;
    private LinearLayout modeCard;
    private LinearLayout chipsRow;
    private HorizontalScrollView chipsScroll;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The window is dark in every system mode, so the light-bar flags have to
        // be cleared or the status bar icons would vanish on a light theme.
        getWindow().getDecorView().setSystemUiVisibility(0);

        palette = new Palette(this);
        thermals = new Thermals(this);
        chrome = chromeScale();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadPresets();

        setContentView(buildShell());
        showPage(PAGE_BRIGHTNESS);
        renderPresets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just come back from the system settings screen.
        if (currentPage == PAGE_BRIGHTNESS) {
            refreshPermissionState();
        }
        startPolling();
    }

    @Override
    protected void onPause() {
        stopPolling();
        super.onPause();
    }

    // ------------------------------------------------------------------- shell

    private View buildShell() {
        int base = palette.dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.surface);
        fitInsets(root, base);

        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Every page is built once and toggled by visibility, so control state
        // survives tab switches.
        pageViews = new View[NAV_PAGES.length];
        for (int i = 0; i < NAV_PAGES.length; i++) {
            View page = buildPage(NAV_PAGES[i]);
            pageViews[i] = page;
            page.setVisibility(View.GONE);
            pageHost.addView(page, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        root.addView(buildNavPill(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    private LinearLayout buildNavPill() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(Palette.ripple(
                Palette.withAlpha(palette.accent, 0.14f), palette.pill(palette.card)));
        pill.setPadding(palette.dp(8), palette.dp(8), palette.dp(8), palette.dp(10));

        navItems = new LinearLayout[NAV_PAGES.length];
        for (int i = 0; i < NAV_PAGES.length; i++) {
            final int page = NAV_PAGES[i];

            ImageView icon = new ImageView(this);
            icon.setImageResource(NAV_ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(palette.textSecondary));

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, palette.dp(8), 0, palette.dp(6));
            item.setBackground(Palette.ripple(
                    Palette.withAlpha(palette.accent, 0.16f), palette.pill(0x00000000)));
            item.addView(icon, new LinearLayout.LayoutParams(palette.dp(24), palette.dp(24)));
            item.setOnClickListener(v -> showPage(page));
            item.setContentDescription(getString(NAV_LABELS[i]));

            navItems[i] = item;
            pill.addView(item, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return pill;
    }

    private void showPage(int page) {
        currentPage = page;
        for (int i = 0; i < pageViews.length; i++) {
            pageViews[i].setVisibility(i == page ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < NAV_PAGES.length; i++) {
            styleNavItem(navItems[i], NAV_PAGES[i] == page);
        }
        if (page == PAGE_BRIGHTNESS) {
            refreshPermissionState();
            startPolling();
        } else {
            stopPolling();
        }
    }

    private void styleNavItem(LinearLayout item, boolean selected) {
        ImageView icon = (ImageView) item.getChildAt(0);
        icon.setImageTintList(ColorStateList.valueOf(
                selected ? palette.textPrimary : palette.textSecondary));
        item.setBackground(Palette.ripple(Palette.withAlpha(palette.accent, 0.16f),
                selected ? palette.pill(palette.segmentSelected) : palette.pill(0x00000000)));
    }

    private void startPolling() {
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void stopPolling() {
        handler.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------- pages

    private View buildPage(int page) {
        return page == PAGE_ABOUT ? buildAboutPage() : buildBrightnessPage();
    }

    /**
     * Fallback scroller for extreme font scales. Scrollbars are switched off in
     * both directions so no indicator is ever drawn, which also stops the
     * overscroll glow on some OEM skins.
     */
    private ScrollView pageScroller(LinearLayout column) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout newPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        return page;
    }

    // ------------------------------------------------------- brightness page

    private View buildBrightnessPage() {
        LinearLayout page = newPage();

        // The only branding on this page: the mark, centred at the top.
        LinearLayout markRow = new LinearLayout(this);
        markRow.setOrientation(LinearLayout.HORIZONTAL);
        markRow.setGravity(Gravity.CENTER);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_duck_mark);
        markRow.addView(mark, new LinearLayout.LayoutParams(px(48), px(48)));
        page.addView(markRow, matchWrap());
        page.addView(space(10));

        // The level card takes the leftover space and shrinks as the cards below
        // it grow, which is what keeps the page filling the screen. On a short
        // screen the other cards would take all of it, so the chrome scale
        // tightens the spacing and the padding first.
        LinearLayout levelCard = card(28);
        pad(levelCard, 24, 18);
        page.addView(levelCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        readoutView = new LevelReadoutView(this);
        readoutView.setColours(palette.textPrimary);

        // The hero: the numeral centred, and its unit parked in the corner. They
        // are siblings rather than one line of text, because a unit that follows
        // the numeral is dragged sideways every time the level changes, and the
        // point of the card is a number that stays put.
        FrameLayout hero = new FrameLayout(this);
        TextView unit = label(getString(R.string.of_max), 16f, palette.textSecondary, 0f,
                Gravity.END, Typeface.DEFAULT);
        // Room for that label, taken from its own measured size rather than a
        // fixed strip, so a large font scale cannot push it up into the numeral.
        float unitHeightDp = unit.getPaint().getTextSize() / getResources()
                .getDisplayMetrics().density;
        FrameLayout.LayoutParams numeralLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        numeralLp.bottomMargin = Math.round(unitHeightDp * 1.45f) + palette.dp(4);
        hero.addView(readoutView, numeralLp);
        hero.addView(unit, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END));
        levelCard.addView(hero, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        meter = new TickMeterView(this);
        // Three steps: unlit, lit, and the single accent tick at the value.
        meter.setColours(palette.accent, palette.track, Palette.withAlpha(palette.accent, 0.45f));
        levelCard.addView(meter, matchWrap());

        rampLabel = label("", 13f, palette.textSecondary, 0f, Gravity.START, Typeface.DEFAULT);
        levelCard.addView(rampLabel, matchWrap(palette.dp(12)));

        modeNote = label("", 13f, palette.accent, 0f, Gravity.START, Typeface.DEFAULT);
        modeNote.setVisibility(View.GONE);
        levelCard.addView(modeNote, matchWrap(palette.dp(4)));

        floatLabel = label("", 13f, palette.textSecondary, 0f, Gravity.START, Typeface.DEFAULT);
        floatLabel.setVisibility(View.GONE);
        levelCard.addView(floatLabel, matchWrap(palette.dp(4)));

        // Device temperature. It is a fact about the phone rather than about
        // the brightness, so it goes last, under the level notes.
        tempLabel = label("", 13f, palette.textSecondary, 0f, Gravity.START, Typeface.DEFAULT);
        tempLabel.setVisibility(View.GONE);
        levelCard.addView(tempLabel, matchWrap(palette.dp(4)));

        // Quick levels: predetermined points on the ramp.
        page.addView(space(12));
        page.addView(buildQuickLevels());

        // Adjust card
        page.addView(space(12));

        LinearLayout adjustCard = card(24);
        pad(adjustCard, 22, 16);
        page.addView(adjustCard, matchWrap());

        slider = new SliderView(this);
        slider.setColours(palette.accent, palette.textPrimary, palette.card, palette.track);
        slider.setOnValueChangedListener((value, fromUser) -> {
            if (fromUser) {
                applyLevel(value);
                renderLevel(value);
            }
        });
        adjustCard.addView(slider, matchWrap());

        // Segmented control. Container is darker than the card and the selected
        // segment is lighter than the container - the contrast order used in the
        // reference set.
        modeCard = card(999);
        modeCard.setBackground(palette.pill(palette.track));
        LinearLayout segmentsRow = new LinearLayout(this);
        segmentsRow.setOrientation(LinearLayout.HORIZONTAL);
        modeCard.addView(segmentsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        adjustCard.addView(modeCard, matchWrap(palette.dp(14)));

        manualSegment = segment(getString(R.string.mode_manual), v -> applyLevel(slider.getValue()));
        autoSegment = segment(getString(R.string.mode_automatic), v -> setModeAutomatic());
        segmentsRow.addView(manualSegment, weighted());
        segmentsRow.addView(autoSegment, weighted());

        saveButton = button(getString(R.string.action_save), palette.onAccent, palette.textPrimary,
                v -> saveCurrent());
        adjustCard.addView(saveButton, matchWrap(palette.dp(14)));

        grantButton = button(getString(R.string.action_grant), palette.textPrimary, 0,
                v -> openWriteSettings());
        grantButton.setVisibility(View.GONE);
        adjustCard.addView(grantButton, matchWrap(palette.dp(14)));

        hintLabel = label(getString(R.string.grant_hint), 12f, palette.textSecondary, 0f,
                Gravity.START, Typeface.DEFAULT);
        hintLabel.setVisibility(View.GONE);
        adjustCard.addView(hintLabel, matchWrap(palette.dp(10)));

        // Saved card
        page.addView(space(12));

        LinearLayout savedCard = card(24);
        pad(savedCard, 22, 16);
        page.addView(savedCard, matchWrap());

        LinearLayout savedHeaderRow = new LinearLayout(this);
        savedHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        savedHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        savedCard.addView(savedHeaderRow, matchWrap());

        savedHeaderRow.addView(label(getString(R.string.section_saved), 11f, palette.textSecondary,
                0.14f, Gravity.START, Typeface.DEFAULT_BOLD), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        savedEmpty = label(getString(R.string.saved_empty), 13f, palette.textSecondary, 0f,
                Gravity.END, Typeface.DEFAULT);
        savedHeaderRow.addView(savedEmpty);

        chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsScroll = chipScroller(chipsRow);
        savedCard.addView(chipsScroll, matchWrap(palette.dp(12)));

        savedCard.addView(label(getString(R.string.preset_hint), 12f, palette.textSecondary, 0f,
                Gravity.START, Typeface.DEFAULT), matchWrap(palette.dp(8)));

        // Keeps the last card clear of the nav pill.
        page.addView(space(BOTTOM_GAP_DP));
        return pageScroller(page);
    }

    /**
     * Predetermined levels at even tenths of the ramp: 25, 51, 76, 102 and so on
     * up to 255. Tapping one applies it, or asks for the permission if that has
     * not been granted yet.
     */
    private LinearLayout buildQuickLevels() {
        LinearLayout quick = card(24);
        pad(quick, 22, 16);
        quick.addView(sectionLabel(getString(R.string.section_quick)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i <= QUICK_STEPS; i++) {
            final int level = (int) (i * MAX_LEVEL / (float) QUICK_STEPS);
            TextView c = chip(String.valueOf(level), palette.accent, palette.track,
                    v -> applyLevelFromChip(level));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = palette.dp(8);
            row.addView(c, lp);
        }
        quick.addView(chipScroller(row), matchWrap(palette.dp(12)));
        return quick;
    }

    // ------------------------------------------------------------- about page

    private View buildAboutPage() {
        LinearLayout page = newPage();

        // Identity card: the mark, centred above the name.
        // Uses the bare duck drawable rather than the adaptive mipmap, which
        // carries its own background layer and shows up as a dark disc.
        LinearLayout identity = card(24);
        identity.setGravity(Gravity.CENTER_HORIZONTAL);
        identity.setPadding(px(22), px(22), px(22), px(20));
        page.addView(identity, matchWrap());

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_duck_mark);
        identity.addView(icon, new LinearLayout.LayoutParams(px(76), px(76)));

        identity.addView(label(getString(R.string.app_name), 22f, palette.textPrimary, 0f,
                Gravity.CENTER, Typeface.DEFAULT_BOLD), matchWrap(palette.dp(6)));
        identity.addView(label(getString(R.string.version_label, versionName()), 13f,
                palette.accent, 0f, Gravity.CENTER, Typeface.DEFAULT));

        // Profile
        page.addView(space(12));

        LinearLayout profile = card(24);
        profile.setOrientation(LinearLayout.HORIZONTAL);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        pad(profile, 18, 14);
        profile.setBackground(Palette.ripple(Palette.withAlpha(palette.accent, 0.12f),
                palette.card(24)));
        profile.setClickable(true);
        profile.setOnClickListener(v -> openUrl(GITHUB_URL));
        page.addView(profile, matchWrap());

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.ic_avatar);
        avatar.setImageTintList(ColorStateList.valueOf(palette.textPrimary));
        // A fully rounded background gives the view a circular outline, and
        // clipping to it trims the portrait to a disc.
        avatar.setBackground(palette.pill(palette.card));
        avatar.setClipToOutline(true);
        profile.addView(avatar, new LinearLayout.LayoutParams(palette.dp(46), palette.dp(46)));

        LinearLayout profileText = new LinearLayout(this);
        profileText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams profileTextLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        profileTextLp.leftMargin = palette.dp(14);
        profile.addView(profileText, profileTextLp);
        profileText.addView(label(getString(R.string.profile_name), 16f, palette.textPrimary, 0f,
                Gravity.START, Typeface.DEFAULT_BOLD));
        profileText.addView(label(getString(R.string.profile_handle), 12f, palette.textSecondary,
                0f, Gravity.START, Typeface.DEFAULT), matchWrap(palette.dp(2)));

        // Why the app asks for anything at all
        page.addView(space(12));
        page.addView(buildPermissionCard());

        // Links
        page.addView(space(12));

        LinearLayout links = card(24);
        pad(links, 6, 6);
        page.addView(links, matchWrap());
        links.addView(linkRow(R.string.link_source, R.string.link_source_sub,
                getString(R.string.link_source_url)));
        links.addView(linkRow(R.string.link_issues, R.string.link_issues_sub,
                getString(R.string.link_issues_url)));
        links.addView(linkRow(R.string.link_releases, R.string.link_releases_sub,
                getString(R.string.link_releases_url)));

        // Device, the block that used to sit in the footer of the first page.
        page.addView(space(12));

        LinearLayout device = card(24);
        pad(device, 22, 16);
        page.addView(device, matchWrap());

        device.addView(label(getString(R.string.device_title), 11f, palette.textSecondary,
                0.14f, Gravity.START, Typeface.DEFAULT_BOLD));
        device.addView(label(getString(R.string.device_model,
                Build.MANUFACTURER, Build.MODEL), 15f, palette.textPrimary, 0f,
                Gravity.START, Typeface.DEFAULT), matchWrap(palette.dp(8)));
        device.addView(label(getString(R.string.device_android,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT), 13f, palette.textSecondary, 0f,
                Gravity.START, Typeface.DEFAULT), matchWrap(palette.dp(4)));
        device.addView(label(getString(R.string.device_targets, TARGET_SDK, MIN_SDK), 13f,
                palette.textSecondary, 0f, Gravity.START, Typeface.DEFAULT));
        device.addView(label(getString(R.string.package_line, getPackageName()), 13f,
                palette.textSecondary, 0f, Gravity.START, Typeface.DEFAULT),
                matchWrap(palette.dp(4)));

        page.addView(space(BOTTOM_GAP_DP));
        return pageScroller(page);
    }

    /**
     * One permission, stated compactly: why it exists, what it is used for, and
     * what it is not used for. The last part matters most - it is the question
     * users actually have about a permission.
     *
     * The heading sits inside the card, as the device heading does, rather than
     * above it: a lone line of text above a card belongs to nothing, and the
     * two card headings then start at the same place.
     */
    private LinearLayout buildPermissionCard() {
        LinearLayout perm = card(24);
        pad(perm, 20, 18);
        perm.addView(sectionLabel(getString(R.string.perm_title)));
        perm.addView(permissionBlock(R.string.perm_key_why, R.string.perm_why),
                matchWrap(palette.dp(14)));
        perm.addView(permissionBlock(R.string.perm_key_does, R.string.perm_does),
                matchWrap(palette.dp(14)));
        perm.addView(permissionBlock(R.string.perm_key_not, R.string.perm_not),
                matchWrap(palette.dp(14)));
        return perm;
    }

    /**
     * Label above, sentence below, rather than a two-column grid: the longest
     * key (DOESN'T) has to fit the key column at a readable size, and squeezing
     * both columns against each other is what made the old version cramped.
     *
     * The sentence is inset on both sides, so it reads as the body under the
     * key instead of a second key starting on the same line, and its line
     * length stays short enough to read comfortably.
     */
    private LinearLayout permissionBlock(int keyRes, int textRes) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(label(getString(keyRes), 11f, palette.accent, 0.14f, Gravity.START,
                Typeface.DEFAULT_BOLD));

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.leftMargin = palette.dp(14);
        textLp.rightMargin = palette.dp(14);
        textLp.topMargin = palette.dp(6);
        block.addView(label(getString(textRes), 13f, palette.textSecondary, 0f, Gravity.START,
                Typeface.DEFAULT), textLp);
        return block;
    }

    // ------------------------------------------------------------- components

    /**
     * Android 15 forces edge-to-edge for targetSdk 35, so without this the
     * content is drawn underneath the status and navigation bars.
     */
    @SuppressWarnings("deprecation")
    private void fitInsets(final View target, final int basePadding) {
        target.setOnApplyWindowInsetsListener((v, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(basePadding + left, basePadding + top,
                    basePadding + right, basePadding + bottom);
            return insets;
        });
        target.requestApplyInsets();
    }

    private TextView label(String text, float sp, int colour, float letterSpacing,
                           int gravity, Typeface face) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(colour);
        tv.setGravity(gravity);
        tv.setTypeface(face);
        if (letterSpacing > 0f) {
            tv.setLetterSpacing(letterSpacing);
        }
        return tv;
    }

    private TextView sectionLabel(String text) {
        return label(text, 11f, palette.textSecondary, 0.14f, Gravity.START, Typeface.DEFAULT_BOLD);
    }

    private View space(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(heightDp)));
        return v;
    }

    /** A dp value scaled by the chrome, so a short screen tightens up. */
    private int px(int dp) {
        return palette.dp(Math.max(2, Math.round(dp * chrome)));
    }

    /** Card padding, scaled by the chrome. Horizontal too: it is the level
     *  card's width that decides how large the numeral can be. */
    private void pad(LinearLayout card, int horizontalDp, int verticalDp) {
        int h = px(horizontalDp);
        card.setPadding(h, px(verticalDp), h, px(verticalDp));
    }

    /**
     * @return how much of the page's spacing and padding to keep, 1 on a screen
     *         with room for the page as drawn and {@link #CHROME_MIN} on the
     *         shortest ones.
     */
    private float chromeScale() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float viewportDp = dm.heightPixels / dm.density - WINDOW_ALLOWANCE_DP;
        return Math.max(CHROME_MIN, Math.min(1f, (viewportDp - CHROME_ROOM) / CHROME_SPAN));
    }

    private LinearLayout card(int radiusDp) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(palette.card(radiusDp));
        return l;
    }

    /** Horizontal chip strip that scrolls but never shows a scrollbar. */
    private HorizontalScrollView chipScroller(LinearLayout row) {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private TextView segment(String text, View.OnClickListener l) {
        TextView tv = label(text, 14f, palette.textSecondary, 0f, Gravity.CENTER, Typeface.DEFAULT_BOLD);
        tv.setPadding(palette.dp(12), palette.dp(11), palette.dp(12), palette.dp(11));
        tv.setClickable(true);
        tv.setOnClickListener(l);
        tv.setBackground(Palette.ripple(Palette.withAlpha(palette.accent, 0.16f), null));
        return tv;
    }

    /** Filled when background is non-zero, outlined otherwise. */
    private TextView button(String text, int textColour, int background, View.OnClickListener l) {
        TextView tv = label(text, 15f, textColour, 0f, Gravity.CENTER, Typeface.DEFAULT_BOLD);
        tv.setPadding(palette.dp(18), palette.dp(15), palette.dp(18), palette.dp(15));
        tv.setBackground(Palette.ripple(Palette.withAlpha(palette.accent, 0.18f),
                background != 0 ? palette.pill(background) : palette.outlined(999, 1)));
        tv.setClickable(true);
        tv.setOnClickListener(l);
        return tv;
    }

    private TextView chip(String text, int textColour, int background, View.OnClickListener l) {
        TextView tv = label(text, 14f, textColour, 0f, Gravity.CENTER, Typeface.DEFAULT_BOLD);
        tv.setPadding(palette.dp(18), palette.dp(10), palette.dp(18), palette.dp(10));
        tv.setBackground(Palette.ripple(Palette.withAlpha(palette.accent, 0.18f),
                palette.pill(background)));
        tv.setClickable(true);
        tv.setOnClickListener(l);
        return tv;
    }

    /**
     * Confirmation prompt, built from the same parts as the pages themselves.
     *
     * android.app.AlertDialog would be shorter, but it takes its colours from
     * the framework theme, which follows the system light and dark setting
     * while this window is dark in both. A dialog drawn from the palette is
     * the only way to keep it consistent, and it reuses the card and the
     * outlined and filled buttons the pages already use.
     */
    private void confirm(String title, String body, String confirmText, final Runnable onConfirm) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = card(28);
        panel.setPadding(palette.dp(22), palette.dp(20), palette.dp(22), palette.dp(18));
        panel.addView(label(title, 18f, palette.textPrimary, 0f, Gravity.START,
                Typeface.DEFAULT_BOLD));
        panel.addView(label(body, 14f, palette.textSecondary, 0f, Gravity.START,
                Typeface.DEFAULT), matchWrap(palette.dp(8)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(actions, matchWrap(palette.dp(20)));

        LinearLayout.LayoutParams cancelLp = weighted();
        cancelLp.rightMargin = palette.dp(10);
        actions.addView(button(getString(R.string.action_cancel), palette.textPrimary, 0,
                v -> dialog.dismiss()), cancelLp);
        actions.addView(button(confirmText, palette.onAccent, palette.accent, v -> {
            dialog.dismiss();
            onConfirm.run();
        }), weighted());

        dialog.setContentView(panel);
        // The default Dialog window is a full screen sheet with its own
        // background; transparent plus a hand set width turns it into a card
        // floating over the page, inset by the page margin on both sides.
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setWindowAnimations(R.style.Animation_HyprDuck_Dialog);
        }
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                WindowManager.LayoutParams lp = shown.getAttributes();
                lp.width = getResources().getDisplayMetrics().widthPixels
                        - palette.dp(40) * 2;
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                shown.setAttributes(lp);
            }
        });
        dialog.show();
    }

    private LinearLayout linkRow(int titleRes, int subtitleRes, String url) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(palette.dp(16), palette.dp(13), palette.dp(12), palette.dp(13));
        row.setBackground(Palette.ripple(Palette.withAlpha(palette.accent, 0.12f), null));
        row.setClickable(true);
        row.setOnClickListener(v -> openUrl(url));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(getString(titleRes), 15f, palette.accent, 0f, Gravity.START,
                Typeface.DEFAULT_BOLD));
        text.addView(label(getString(subtitleRes), 12f, palette.textSecondary, 0f, Gravity.START,
                Typeface.DEFAULT));
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(label("›", 22f, palette.textSecondary, 0f, Gravity.CENTER, Typeface.DEFAULT));
        return row;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return matchWrap(0);
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    // ---------------------------------------------------------------- reading

    private void refresh() {
        if (currentPage != PAGE_BRIGHTNESS) {
            return;
        }
        int level = readInt(Settings.System.SCREEN_BRIGHTNESS, -1);
        renderLevel(level);
        renderMode(readInt(Settings.System.SCREEN_BRIGHTNESS_MODE, -1));
        renderTemperature();

        if (!slider.isDragging() && level >= 0) {
            slider.setValue(level);
        }
    }

    private void renderLevel(int level) {
        meter.setLevel(level);

        if (level < 0) {
            readoutView.setValue("--");
            rampLabel.setText(R.string.unavailable);
            floatLabel.setVisibility(View.GONE);
            return;
        }

        readoutView.setValue(String.valueOf(level));
        rampLabel.setText(getString(R.string.ramp_percent, (int) Math.round(100.0 * level / MAX_LEVEL)));

        // HyperOS usually does not publish screen_brightness_float, so this only
        // appears when the value exists.
        float stored = readFloat("screen_brightness_float", -1f);
        if (stored >= 0f) {
            floatLabel.setVisibility(View.VISIBLE);
            floatLabel.setText(getString(R.string.float_value, stored));
        } else {
            floatLabel.setVisibility(View.GONE);
        }
    }

    /**
     * Temperature comes from a sysfs node the kernel may not expose, so a
     * device with nothing readable shows no line rather than a made up one.
     */
    private void renderTemperature() {
        Thermals.Reading reading = thermals.read();
        if (reading == null) {
            tempLabel.setVisibility(View.GONE);
            return;
        }
        tempLabel.setVisibility(View.VISIBLE);
        tempLabel.setText(getString(R.string.temp_value, reading.source, reading.celsius));
    }

    private void renderMode(int mode) {
        boolean auto = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        styleSegment(manualSegment, !auto);
        styleSegment(autoSegment, auto);

        // In automatic mode the stored level is only the sensor's last target, so
        // say so instead of implying the number is the current brightness.
        int level = readInt(Settings.System.SCREEN_BRIGHTNESS, -1);
        if (auto) {
            modeNote.setVisibility(View.VISIBLE);
            modeNote.setText(R.string.note_auto);
        } else if (level == 0) {
            modeNote.setVisibility(View.VISIBLE);
            modeNote.setText(R.string.mode_default);
        } else {
            modeNote.setVisibility(View.GONE);
        }
    }

    private void styleSegment(TextView segment, boolean selected) {
        segment.setTextColor(selected ? palette.textPrimary : palette.textSecondary);
        segment.setBackground(Palette.ripple(Palette.withAlpha(palette.accent, 0.16f),
                selected ? palette.pill(palette.segmentSelected) : null));
    }

    /** Shows the controls that WRITE_SETTINGS unlocks, hides the rest. */
    private void refreshPermissionState() {
        boolean granted = Settings.System.canWrite(this);

        slider.setVisibility(granted ? View.VISIBLE : View.GONE);
        modeCard.setVisibility(granted ? View.VISIBLE : View.GONE);
        saveButton.setVisibility(granted ? View.VISIBLE : View.GONE);
        grantButton.setVisibility(granted ? View.GONE : View.VISIBLE);
        hintLabel.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    // ---------------------------------------------------------------- writing

    /** Applies a level from a chip, or asks for permission if not granted. */
    private void applyLevelFromChip(int level) {
        if (!Settings.System.canWrite(this)) {
            openWriteSettings();
            return;
        }
        applyLevel(level);
        slider.setValue(level);
    }

    private void applyLevel(int value) {
        if (!Settings.System.canWrite(this)) {
            return;
        }
        ContentResolver cr = getContentResolver();
        // A stored level has no effect while the sensor is driving brightness, so
        // adjusting implies manual.
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, clamp(value));
        renderMode(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    private void setModeAutomatic() {
        if (!Settings.System.canWrite(this)) {
            return;
        }
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        renderMode(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
    }

    private void openWriteSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.link_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    // --------------------------------------------------------------- presets

    private void loadPresets() {
        presets.clear();
        String raw = prefs.getString(KEY_PRESETS, "");
        if (raw.isEmpty()) {
            return;
        }
        for (String part : raw.split(",")) {
            try {
                presets.add(clamp(Integer.parseInt(part.trim())));
            } catch (NumberFormatException ignored) {
                // Skip anything corrupt rather than losing the rest.
            }
        }
    }

    private void saveCurrent() {
        int level = readInt(Settings.System.SCREEN_BRIGHTNESS, -1);
        if (level < 0) {
            Toast.makeText(this, R.string.preset_none, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!presets.contains(level)) {
            if (presets.size() >= MAX_PRESETS) {
                Toast.makeText(this, getString(R.string.preset_full, MAX_PRESETS),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            presets.add(level);
            persistPresets();
        }
        Toast.makeText(this, getString(R.string.preset_saved, level), Toast.LENGTH_SHORT).show();
    }

    private void removePreset(int level) {
        if (presets.remove(level)) {
            persistPresets();
            Toast.makeText(this, getString(R.string.preset_removed, level), Toast.LENGTH_SHORT).show();
        }
    }

    private void persistPresets() {
        StringBuilder sb = new StringBuilder();
        for (int p : presets) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(p);
        }
        prefs.edit().putString(KEY_PRESETS, sb.toString()).apply();
        renderPresets();
    }

    private void renderPresets() {
        chipsRow.removeAllViews();

        boolean any = !presets.isEmpty();
        savedEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
        chipsScroll.setVisibility(any ? View.VISIBLE : View.GONE);

        for (final int level : presets) {
            TextView tv = chip(String.valueOf(level), palette.accent, palette.track,
                    v -> applyLevelFromChip(level));
            // A hold is easy to trigger by accident while scrolling the chip
            // strip, and a saved level is not easy to get back, so removing one
            // is confirmed rather than done on the spot.
            tv.setOnLongClickListener(v -> {
                confirm(getString(R.string.preset_delete_title, level),
                        getString(R.string.preset_delete_body, level),
                        getString(R.string.preset_delete_confirm),
                        () -> removePreset(level));
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = palette.dp(8);
            chipsRow.addView(tv, lp);
        }
    }

    // ---------------------------------------------------------------- helpers

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private int readInt(String key, int fallback) {
        try {
            return Settings.System.getInt(getContentResolver(), key);
        } catch (Settings.SettingNotFoundException | SecurityException e) {
            return fallback;
        }
    }

    private float readFloat(String key, float fallback) {
        try {
            return Settings.System.getFloat(getContentResolver(), key);
        } catch (Settings.SettingNotFoundException | SecurityException e) {
            return fallback;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(MAX_LEVEL, v));
    }
}
