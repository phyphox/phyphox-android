package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.CRC32;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

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

    public static float luminance(int c) {
        float r = ((c & 0xff0000) >> 16)/255f;
        float g = ((c & 0xff00) >> 8)/255f;
        float b = (c & 0xff)/255f;
        return 0.2126f*(float)Math.pow((r+0.055f)/1.055f, 2.4f) + 0.7152f*(float)Math.pow((g+0.055f)/1.055f, 2.4f) + 0.0722f*(float)Math.pow((b+0.055f)/1.055f, 2.4f);
    }

    //Translate RGB values or pre-defined names into integer representations
    public static int parseColor(String colorStr, int defaultValue, Resources res) {
        if (colorStr == null)
            return defaultValue;
        //We first check for specific names. As we do not set prefix (like a hash), we have to be careful that these constants do not colide with a valid hex representation of RGB
        switch(colorStr.toLowerCase()) {
            case "orange": return res.getColor(R.color.presetOrange);
            case "red": return res.getColor(R.color.presetRed);
            case "magenta": return res.getColor(R.color.presetMagenta);
            case "blue": return res.getColor(R.color.presetBlue);
            case "green": return res.getColor(R.color.presetGreen);
            case "yellow": return res.getColor(R.color.presetYellow);
            case "white": return res.getColor(R.color.presetWhite);

            case "weakorange": return res.getColor(R.color.presetWeakOrange);
            case "weakred": return res.getColor(R.color.presetWeakRed);
            case "weakmagenta": return res.getColor(R.color.presetWeakMagenta);
            case "weakblue": return res.getColor(R.color.presetWeakBlue);
            case "weakgreen": return res.getColor(R.color.presetWeakGreen);
            case "weakyellow": return res.getColor(R.color.presetWeakYellow);
            case "weakwhite": return res.getColor(R.color.presetWeakWhite);
        }

        //Not a constant, so it hast to be hex...
        if (colorStr.length() != 6)
            return defaultValue;
        return Integer.parseInt(colorStr, 16) | 0xff000000;
    }

    public static void replaceTagInFile(String file, Context ctx, String tag, String newContent) {
        try {
            FileInputStream in = ctx.openFileInput(file);
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            in.close();

            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xpath.evaluate(tag, doc, XPathConstants.NODESET);

            for (int i = 0; i < nodes.getLength(); i++) {
                nodes.item(i).setTextContent(newContent);
            }

            Transformer t = TransformerFactory.newInstance().newTransformer();
            FileOutputStream out = ctx.openFileOutput(file, 0);
            t.transform(new DOMSource(doc), new StreamResult(out));
            out.close();
        } catch (Exception e) {
            Log.e("replace tag", "Could not replace tag: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean experimentInCollection(long refCRC32, Activity act) {
        CRC32 crc32 = new CRC32();

        File[] files = act.getFilesDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".phyphox");
            }
        });

        boolean found = false;

        for (File file : files) {
            crc32.reset();

            try {
                InputStream input = new FileInputStream(file);
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    crc32.update(buffer, 0, count);
                }
            }catch (Exception e) {
                continue;
            }
            if (refCRC32 == crc32.getValue()) {
                found = true;
                break;
            }
        }

        return found;
    }

    public static boolean experimentInCollection(File file, Activity act) {
        CRC32 crc32 = new CRC32();
        try {
            InputStream input = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                crc32.update(buffer, 0, count);
            }
            input.close();
        }catch (Exception e) {
            return false;
        }

        long refCRC32 = crc32.getValue();

        return experimentInCollection(refCRC32, act);
    }
}
