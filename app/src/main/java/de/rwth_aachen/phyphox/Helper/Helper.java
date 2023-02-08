package de.rwth_aachen.phyphox.Helper;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.content.ContextCompat;

import androidx.preference.PreferenceManager;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.Locale;
import java.util.Vector;
import java.util.zip.CRC32;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import de.rwth_aachen.phyphox.InteractiveGraphView;
import de.rwth_aachen.phyphox.PlotAreaView;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.SettingsFragment;

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
            case "orange": return res.getColor(R.color.phyphox_primary);
            case "red": return res.getColor(R.color.phyphox_red);
            case "magenta": return res.getColor(R.color.phyphox_magenta);
            case "blue":
                if(isDarkTheme(res))
                    return res.getColor(R.color.phyphox_blue_60);
                else
                    return res.getColor(R.color.phyphox_blue_strong);
            case "green":
                if(isDarkTheme(res))
                    return res.getColor(R.color.phyphox_green);
                else
                    return res.getColor(R.color.phyphox_green_strong);
            case "yellow":
                if(isDarkTheme(res))
                    return res.getColor(R.color.phyphox_yellow);
                else
                    return res.getColor(R.color.phyphox_yellow_strong);
            case "white":
                if(isDarkTheme(res))
                    return res.getColor(R.color.phyphox_white_100);
                else
                    return res.getColor(R.color.phyphox_black_100);

            case "weakorange": return res.getColor(R.color.phyphox_primary_weak);
            case "weakred": return res.getColor(R.color.phyphox_red_weak);
            case "weakmagenta": return res.getColor(R.color.phyphox_magenta_weak);
            case "weakblue": return res.getColor(R.color.phyphox_blue_40);
            case "weakgreen": return res.getColor(R.color.phyphox_green_weak);
            case "weakyellow": return res.getColor(R.color.phyphox_yellow_weak);
            case "weakwhite": return res.getColor(R.color.phyphox_white_60);
        }

        //Not a constant, so it hast to be hex...
        if (colorStr.length() != 6)
            return defaultValue;
        return Integer.parseInt(colorStr, 16) | 0xff000000;
    }

    public static boolean isDarkTheme(Resources res){
        int nightModelFlags = res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        switch (nightModelFlags){
            case Configuration.UI_MODE_NIGHT_YES:
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
                return true;
            case Configuration.UI_MODE_NIGHT_NO:
                return false;
        }
        return false;
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

    //Recursively get all TextureViews, used for screenshots
    public static Vector<PlotAreaView> getAllPlotAreaViews(View v) {
        Vector<PlotAreaView> l = new Vector<>();
        if (v.getVisibility() != View.VISIBLE)
            return l;
        if (v instanceof PlotAreaView) {
            l.add((PlotAreaView)v);
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                l.addAll(getAllPlotAreaViews(vg.getChildAt(i)));
            }
        }
        return l;
    }

    public static Vector<InteractiveGraphView> getAllInteractiveGraphViews(View v) {
        Vector<InteractiveGraphView> l = new Vector<>();
        if (v.getVisibility() != View.VISIBLE)
            return l;
        if (v instanceof InteractiveGraphView) {
            l.add((InteractiveGraphView)v);
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                l.addAll(getAllInteractiveGraphViews(vg.getChildAt(i)));
            }
        }
        return l;
    }

    public interface ScreenshotCallback {
        void onSuccess(Bitmap bitmap);
    }
    public static void getScreenshot(View screenView, Window window, ScreenshotCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Bitmap bitmap = Bitmap.createBitmap(screenView.getWidth(), screenView.getHeight(), Bitmap.Config.ARGB_8888);
            int location[] = new int[2];
            screenView.getLocationInWindow(location);

            PixelCopy.OnPixelCopyFinishedListener listener = new PixelCopy.OnPixelCopyFinishedListener() {
                @Override
                public void onPixelCopyFinished(int result) {
                    if (result == PixelCopy.SUCCESS)
                        callback.onSuccess(bitmap);
                }
            };

            PixelCopy.request(window, new Rect(location[0], location[1], location[0] + screenView.getWidth(), location[1] + screenView.getHeight()), bitmap, listener, new Handler());

        } else { //Fallback if Pixel Copy API is not available
            screenView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(screenView.getDrawingCache());
            screenView.setDrawingCacheEnabled(false);

            Canvas canvas = new Canvas(bitmap);

            Vector<PlotAreaView> pavList = getAllPlotAreaViews(screenView);
            for (PlotAreaView pav : pavList) {
                pav.setDrawingCacheEnabled(true);
                Bitmap bmp = pav.getBitmap();
                pav.setDrawingCacheEnabled(false);

                int location[] = new int[2];
                pav.getLocationOnScreen(location);
                canvas.drawBitmap(bmp, location[0], location[1], null);
            }

            Vector<InteractiveGraphView> gvList = getAllInteractiveGraphViews(screenView);
            for (InteractiveGraphView gv : gvList) {
                try {
                    gv.setDrawingCacheEnabled(true);
                    Bitmap bmp = Bitmap.createBitmap(gv.getDrawingCache());
                    gv.setDrawingCacheEnabled(false);

                    int location[] = new int[2];
                    gv.getLocationOnScreen(location);
                    canvas.drawBitmap(bmp, location[0], location[1], null);

                    if (gv.popupWindowInfo != null) {
                        View popupView = gv.popupWindowInfo.getContentView();
                        popupView.setDrawingCacheEnabled(true);
                        bmp = Bitmap.createBitmap(popupView.getDrawingCache());
                        popupView.setDrawingCacheEnabled(false);

                        popupView.getLocationOnScreen(location);
                        canvas.drawBitmap(bmp, location[0], location[1], null);
                    }
                } catch (Exception e) {
                    //getDrawingCache can fail in landscape orientation as plots may use a rather large area of screen that will not fit into a software buffer.
                    //We only catch it without a solution (which would be tricky) as this method is a fallback anyways for API <24 devices that do not support Pixel Copy API. This should occur rarely.
                    e.printStackTrace();
                }


            }
            callback.onSuccess(bitmap);
        }
    }

    //Decode the experiment icon (base64) and return a bitmap
    public static Bitmap decodeBase64(String input) throws IllegalArgumentException {
        byte[] decodedByte = Base64.decode(input, 0); //Decode the base64 data to binary
        return BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length); //Interpret the binary data and return the bitmap
    }

    public static Bitmap changeColorOf(Context context,Bitmap bitmap, int colorId){
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Paint paint = new Paint();
        ColorFilter filter = new PorterDuffColorFilter(ContextCompat.getColor(context, colorId), PorterDuff.Mode.SRC_IN);
        paint.setColorFilter(filter);

        Canvas canvas = new Canvas(mutableBitmap);
        canvas.drawBitmap(mutableBitmap, 0, 0, paint);

        return mutableBitmap;
    }

    private static int getResourceId(GraphField field, int size){
        switch (field){
            case LABEL_SIZE:
                if(size == FieldSize.SMALL){
                    return R.dimen.label_size_small;
                } else if(size == FieldSize.BIG){
                    return R.dimen.label_size_big;
                } else if(size == FieldSize.BIGGER){
                    return R.dimen.label_size_bigger;
                } else{
                    return R.dimen.label_size_medium;
                }
            case TEXT_SIZE:
                if(size==FieldSize.SMALL){
                    return R.dimen.text_size_small;
                } else if(size==FieldSize.BIG){
                    return R.dimen.text_size_big;
                } else if(size==FieldSize.BIGGER){
                    return R.dimen.text_size_bigger;
                } else{
                    return R.dimen.text_size_medium;
                }
            case LINE_WIDTH:
                if(size==FieldSize.SMALL){
                    return R.dimen.line_width_small;
                } else if(size==FieldSize.BIG){
                    return R.dimen.line_width_big;
                } else if(size==FieldSize.BIGGER){
                    return R.dimen.line_width_bigger;
                } else{
                    return R.dimen.line_width_medium;
                }
            case BORDER_WIDTH:
                if(size==FieldSize.SMALL){
                    return R.dimen.border_width_small;
                } else if(size==FieldSize.BIG){
                    return R.dimen.border_width_big;
                } else if(size==FieldSize.BIGGER){
                    return R.dimen.border_width_bigger;
                } else{
                    return R.dimen.border_width_medium;
                }
        }
        return 0;
    }

    public static float getUserSelectedGraphSetting(Context context, GraphField field) {
        int savedSize = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getInt(SettingsFragment.GRAPH_SIZE_KEY, FieldSize.MEDIUM);
        int resourceId = getResourceId(field,savedSize);
        return context.getResources().getDimension(resourceId);
    }

    public enum GraphField {
        LABEL_SIZE, TEXT_SIZE, LINE_WIDTH, BORDER_WIDTH
    }

    public static class FieldSize {
        public final static int SMALL = 0;
        public final static int MEDIUM = 1;
        public final static int BIG = 2;
        public final static int BIGGER = 3;
    }
}
