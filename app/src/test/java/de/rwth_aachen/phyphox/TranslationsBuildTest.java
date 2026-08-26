package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// phyphox-test: translations-build
//Language handling follows the build: every locale the build enables (BuildConfig.LOCALE_ARRAY)
//must actually resolve its resources, and the enabled set is compared against the canonical list
//in phyphox-docs (languages.yml).
//
//Deviations from that list are WARNINGS, never failures - that is the ruled mechanism (area P of
//the test plan): during development a locale is enabled for testing or a new translation lands
//before the list is updated, and both should stay visible without breaking the build. The hard
//enforcement lives at T2, against the built artifact.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TranslationsBuildTest {

    //Android's resource qualifiers spell some languages differently from BCP-47, which is what
    //the canonical list uses (its header names exactly these).
    private static String normalize(String qualifier) {
        switch (qualifier) {
            case "zh-rCN":
                return "zh-Hans";
            case "zh-rTW":
                return "zh-Hant";
            case "b+sr+Latn":
                return "sr-Latn";
            default:
                return qualifier;
        }
    }

    //A locale as Android wants it for a resource lookup.
    private static Locale localeOf(String qualifier) {
        if (qualifier.startsWith("b+"))
            return Locale.forLanguageTag(qualifier.substring(2).replace('+', '-'));
        return Locale.forLanguageTag(qualifier.replace("-r", "-"));
    }

    private Set<String> enabledLocales() {
        return new LinkedHashSet<>(java.util.Arrays.asList(BuildConfig.LOCALE_ARRAY));
    }

    @Test
    public void everyEnabledLocaleResolvesItsResources() {
        Context context = ApplicationProvider.getApplicationContext();
        List<String> findings = new ArrayList<>();

        for (String qualifier : enabledLocales()) {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.setLocale(localeOf(qualifier));
            Resources resources = context.createConfigurationContext(configuration).getResources();

            //Any string the app always needs; an unresolvable locale throws or falls back to an
            //empty resource table, and both show up here.
            String text = resources.getString(R.string.app_name);
            if (text == null || text.isEmpty())
                findings.add(qualifier + ": resources do not resolve");
        }

        assertTrue(String.join("\n  ", findings), findings.isEmpty());
    }

    @Test
    public void theEnabledSetIsReportedAgainstTheCanonicalList() throws IOException {
        Set<String> enabled = new TreeSet<>();
        for (String qualifier : enabledLocales())
            enabled.add(normalize(qualifier));

        Set<String> canonical = canonicalLanguages();
        if (canonical == null) {
            System.out.println("NOTICE: No phyphox-docs checkout next to this repository - the "
                    + "canonical language list was not compared.");
            return;
        }

        Set<String> extra = new TreeSet<>(enabled);
        extra.removeAll(canonical);
        Set<String> missing = new TreeSet<>(canonical);
        missing.removeAll(enabled);

        //Warnings, by decision: development drift is harmless and stays visible. The release
        //gate is the T2 check against the built artifact.
        if (!extra.isEmpty())
            System.out.println("WARNING [translations-build]: enabled but not in the canonical "
                    + "list (testing locales, or a new translation the list does not know yet): "
                    + String.join(", ", extra));
        if (!missing.isEmpty())
            System.out.println("WARNING [translations-build]: in the canonical list but not "
                    + "enabled by this build: " + String.join(", ", missing));
        if (extra.isEmpty() && missing.isEmpty())
            System.out.println("translations-build: the build matches the canonical list exactly.");

        //English must be there whatever else drifts - everything falls back to it.
        assertTrue("the build enables no English resources", enabled.contains("en"));
        assertFalse("the build enables no locales at all", enabled.isEmpty());
    }

    private Set<String> canonicalLanguages() throws IOException {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        File list = null;
        for (int i = 0; i < 8 && dir != null; i++) {
            File candidate = new File(dir, "phyphox-docs/languages.yml");
            if (candidate.isFile()) {
                list = candidate;
                break;
            }
            dir = dir.getParentFile();
        }
        if (list == null)
            return null;

        //A flat "languages:" list of scalars - read without a YAML dependency in the test source
        //set, the way the corpus expectations are read.
        Set<String> languages = new TreeSet<>();
        boolean inList = false;
        Pattern entry = Pattern.compile("^\\s*-\\s*([A-Za-z0-9-]+)\\s*$");
        for (String line : Files.readAllLines(list.toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("languages:")) {
                inList = true;
                continue;
            }
            if (inList) {
                Matcher matcher = entry.matcher(line);
                if (matcher.matches())
                    languages.add(matcher.group(1));
                else if (!line.trim().isEmpty() && !line.trim().startsWith("#"))
                    break;
            }
        }
        return languages;
    }
}
