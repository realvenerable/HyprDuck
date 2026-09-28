package dev.realvenerable.hyprduck;

import android.content.Context;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Device temperature, read from the nodes the kernel already exposes.
 *
 * There is no public API for a temperature reading. HardwarePropertiesManager
 * exists, but its getDeviceTemperances() is gated behind DEVICE_POWER, which is
 * signature-only, so an ordinary app cannot call it. What is left are the sysfs
 * nodes: the battery's own thermistor, and the thermal zones. The kernel is
 * free to restrict any of them and most vendors restrict some, so every read is
 * best effort and a device that publishes nothing is reported as unavailable
 * rather than filled in with a guess.
 *
 * None of this needs a permission, which is why nothing was added to the
 * manifest for it.
 *
 * The nodes are re-read at most every few seconds. The poll that drives the
 * level readout runs twice a second, and touching the filesystem that often
 * costs more than the extra precision is worth.
 */
final class Thermals {

    /** Long enough that the value is not stale, short enough to feel live. */
    private static final long CACHE_MS = 4000L;

    /** No probe has run yet. */
    private static final long NEVER = -1L;

    private static final String BATTERY_DIR = "/sys/class/power_supply/battery";
    private static final String THERMAL_DIR = "/sys/class/thermal";

    /** Readings outside this are a misread scale or a broken node, not weather. */
    private static final float MIN_PLAUSIBLE = -20f;
    private static final float MAX_PLAUSIBLE = 120f;

    /**
     * A zone raw value above this is not millidegrees Celsius, because that
     * would be 150 °C, so it is read as millikelvin instead. The two
     * interpretations only overlap between 120 °C and 0 °C, and nothing reports
     * a temperature in that band, so either reading of a value in it is
     * discarded by the plausibility check and the exact cut does not matter.
     */
    private static final long ZONE_KELVIN_FLOOR = 150000L;

    /**
     * Label per zone rank, index 0 being the least specific. Kept in one place
     * so the ranking and the naming cannot drift apart.
     */
    private static final int[] SOURCE_LABELS = {
            R.string.temp_source_device,
            R.string.temp_source_shell,
            R.string.temp_source_soc,
            R.string.temp_source_skin,
            R.string.temp_source_battery};

    private final Context context;
    private long stamp = NEVER;
    private Reading cached;

    Thermals(Context context) {
        this.context = context;
    }

    /** A temperature and the node it came from, or null if there is none. */
    static final class Reading {
        final String source;
        final float celsius;

        Reading(String source, float celsius) {
            this.source = source;
            this.celsius = celsius;
        }
    }

    /**
     * @return the current reading, or null when no node could be read. Cached
     *         for {@link #CACHE_MS}, because the caller polls far faster than
     *         the value moves. A miss is cached too, so a device that hides
     *         every node is probed as rarely as one that answers.
     */
    Reading read() {
        long now = SystemClock.elapsedRealtime();
        if (stamp != NEVER && now - stamp < CACHE_MS) {
            return cached;
        }
        stamp = now;
        cached = batteryReading();
        if (cached == null) {
            cached = zoneReading();
        }
        return cached;
    }

    /**
     * The battery thermistor, which the power supply framework writes in
     * deci-Kelvin, so a room temperature cell reads around 2980. A few vendors
     * publish milli-Kelvin instead, an order of magnitude larger, and reading
     * that as deci-Kelvin would show a temperature 270 degrees below freezing.
     * The two scales are far enough apart to tell apart, because no battery sits
     * below 250 K or above 350 K.
     */
    private Reading batteryReading() {
        long raw = readLong(new File(BATTERY_DIR, "temp"));
        if (raw <= 0) {
            return null;
        }
        float kelvin = raw > 10000 ? raw / 1000f : raw / 10f;
        float celsius = kelvin - 273.15f;
        if (!plausible(celsius)) {
            return null;
        }
        return new Reading(context.getString(R.string.temp_source_battery), celsius);
    }

    /**
     * The thermal zones, best match first. The battery is preferred over the
     * SoC because it is the part of the phone a person is holding, and skin
     * over both because that is what the device feels like from the outside.
     * An unnamed zone is still a real reading, so it is kept as the last
     * resort rather than discarded.
     */
    private Reading zoneReading() {
        File[] zones = new File(THERMAL_DIR).listFiles();
        if (zones == null) {
            // Nothing to list: the directory is hidden from apps on this device.
            return null;
        }

        Reading best = null;
        int bestRank = -1;
        for (File zone : zones) {
            if (!zone.getName().startsWith("thermal_zone")) {
                continue;
            }
            long raw = readLong(new File(zone, "temp"));
            if (raw <= 0) {
                continue;
            }
            float celsius = zoneCelsius(raw);
            if (!plausible(celsius)) {
                continue;
            }
            int rank = rankOf(readString(new File(zone, "type")));
            if (rank <= bestRank) {
                continue;
            }
            bestRank = rank;
            best = new Reading(context.getString(SOURCE_LABELS[rank]), celsius);
        }
        return best;
    }

    /**
     * Zone types are kernel strings such as "skin", "soc", "quiet" or
     * "batt". A phone reports several zones, so the most useful one wins rather
     * than whichever the kernel happened to number first.
     */
    private static int rankOf(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.contains("batt")) {
            return 4;
        }
        if (t.contains("skin")) {
            return 3;
        }
        if (t.contains("soc") || t.contains("cpu") || t.contains("ap")) {
            return 2;
        }
        if (t.contains("shell") || t.contains("case")) {
            return 1;
        }
        return 0;
    }

    /** See {@link #ZONE_KELVIN_FLOOR} for why the two scales are told apart. */
    private static float zoneCelsius(long raw) {
        float celsius = raw / 1000f;
        return raw > ZONE_KELVIN_FLOOR ? celsius - 273.15f : celsius;
    }

    private static boolean plausible(float celsius) {
        return celsius >= MIN_PLAUSIBLE && celsius <= MAX_PLAUSIBLE;
    }

    private static long readLong(File file) {
        String text = readString(file);
        if (text == null) {
            return -1;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** @return the whole node, or null when it cannot be opened or is empty. */
    private static String readString(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64];
            int read = in.read(buffer);
            if (read <= 0) {
                return null;
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            // Unreadable on this device, which is the normal case, not an error.
            return null;
        }
    }
}
