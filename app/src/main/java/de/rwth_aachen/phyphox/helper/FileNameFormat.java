package de.rwth_aachen.phyphox.helper;

import android.content.Context;

import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.rwth_aachen.phyphox.ExperimentTimeReference;

//Default file names for exports, screenshots and saved states from the user's template (settings) with placeholders like {title} or {date}
public class FileNameFormat {

    public static final String PREF_KEY = "fileNameFormat";
    public static final String DEFAULT_FORMAT = "{title} {date} {time}";

    private static final String FALLBACK_NAME = "phyphox";

    public static String getFormat(Context ctx) {
        String format = PreferenceManager.getDefaultSharedPreferences(ctx).getString(PREF_KEY, DEFAULT_FORMAT);
        if (format == null || format.trim().isEmpty())
            return DEFAULT_FORMAT;
        return format;
    }

    //Not sanitized (may serve as a title); use sanitize() or formatFilename() for a file name
    public static String format(Context ctx, String title, ExperimentTimeReference timeReference) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH-mm-ss", Locale.US);

        Date now = new Date();
        Date start = now;
        double duration = 0.0;
        if (timeReference != null && timeReference.hasMappings()) {
            start = new Date(timeReference.getSystemTimeReferenceByIndex(0));
            duration = timeReference.getExperimentTime();
        }

        return getFormat(ctx)
                .replace("{title}", (title == null || title.isEmpty()) ? FALLBACK_NAME : title)
                .replace("{date}", dateFormat.format(now))
                .replace("{time}", timeFormat.format(now))
                .replace("{startDate}", dateFormat.format(start))
                .replace("{startTime}", timeFormat.format(start))
                .replace("{duration}", String.format(Locale.US, "%.1fs", duration));
    }

    public static String sanitize(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "").trim();
        return sanitized.isEmpty() ? FALLBACK_NAME : sanitized;
    }

    public static String formatFilename(Context ctx, String title, ExperimentTimeReference timeReference) {
        return sanitize(format(ctx, title, timeReference));
    }
}
