package de.rwth_aachen.phyphox;

import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import java.util.Locale;

public abstract class Helper {

    private static Locale getResLocale(Resources res) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return res.getConfiguration().getLocales().get(0);
        } else {
            return res.getConfiguration().locale;
        }
    }

    public static int getLanguageRating(Resources res, String language) {
        if (language == null || language.isEmpty())
            return 1; //This can only happen to the base translation. Its language is not specified, but it's probably better for the target audience than a non-matching language - with one exception: If the base language is not specified and an English block is declared as a translation, then the base language is probably just a place-holder, so English is preferred in this case.

        int score = 0;

        //We compare the found language to the one picked by Android from the ressources
        //This prevents a selection of languages to which phyphox has never been translated, but in
        // most such cases, there is only one language without an explicit locale, in which case
        // this works fine. If we checked against user preference, the selected locale might not
        // match the one picked by Android (for example, we have zh-TW and zh-CN, but the user has
        // chosen zh_HK. Android might pick zh-TW by knowing that both are traditional Chinese, but
        // our simple algorithm might pick zh-CN as it was defined later in the document. In a
        // similar case, Android might decide that the regions are too different to be a match.)

        String[] parts = language.split("[-_]");
        String baseLanguage = "";
        String region = "";
        String script = "";
        if (parts.length > 0)
            baseLanguage = parts[0].toLowerCase();
        if (parts.length > 1) {
            region = parts[parts.length - 1].toLowerCase();
            if (parts.length > 2)
                script = parts[1].toLowerCase();
            else
                script = region;
        }

        Locale resLocale = getResLocale(res);
        String resLanguage = resLocale.getLanguage().toLowerCase();
        String resRegion = resLocale.getCountry().toLowerCase();

        //Rule: Same base language? That is a pretty good match...
        if (baseLanguage.equals(resLanguage))
            score+=100;

        //Rule: Same country/region? Even better...
        if (region.equals(resRegion))
            score+=20;

        //Add scores for known related languages and variants (unfortunately not easily supported on older Android versions)
        if (resLanguage.equals("zh")) {
            if (resRegion.equals("hk") || resRegion.equals("mo") || resRegion.equals("tw")) {
                if (script.equals("hant"))
                    score += 10;
            }
            if (resRegion.equals("cn") || resRegion.equals("mo") || resRegion.equals("sg")) { //To my knowledge traditional and simplified are both used in Macau, so this is redundant. If someone knows this region better, we can add a preference here
                if (script.equals("hans"))
                    score += 10;
            }
        }

        //Rule: We slightly prefer English as an international fallback
        if (baseLanguage.equals("en"))
            score+=2;

        return score;
    }
}
