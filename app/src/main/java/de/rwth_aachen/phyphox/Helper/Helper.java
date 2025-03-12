package de.rwth_aachen.phyphox.Helper;

import static android.content.Context.AUDIO_SERVICE;
import static android.content.Context.BATTERY_SERVICE;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        return experimentInCollection(getCRC32(file), act);
    }

    public static long getCRC32(File file) {
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
            Log.e("getCRC32", "Exception: " + e.getMessage());
            return 0;
        }

        return crc32.getValue();
    }

    //Thanks to https://stackoverflow.com/a/9293885/8068814
    public static void copyFile(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
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

    public static ColorFilter getAdjustedColorForImage(Context context){
        if(isDarkTheme(context.getResources())){
           return new PorterDuffColorFilter(ContextCompat.getColor(context, R.color.phyphox_white_100), PorterDuff.Mode.SRC_IN);
        } else {
            return new PorterDuffColorFilter(ContextCompat.getColor(context, R.color.phyphox_black_100), PorterDuff.Mode.SRC_IN);
        }
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

    public static byte [] inflatePartialZip(byte [] dataReceived) {
        byte [] zipData;
        int totalSize = dataReceived.length;
        if (dataReceived[totalSize-16] == 0x50 && dataReceived[totalSize-15] == 0x4b && dataReceived[totalSize-14] == 0x07 && dataReceived[totalSize-13] == 0x08) {
            //This is a data descriptor we found at the end of the QR code data.
            //Therefore, we did not receive a complete zip file, but a partial one that
            // only contains a single entry and omits the local file header as well as
            // the central directory
            //We have to add those ourselves.

            zipData = new byte[39 + totalSize - 16 + 55 + 22];

            //Local file header
            zipData[0] = 0x50; //Local file header signature
            zipData[1] = 0x4b;
            zipData[2] = 0x03;
            zipData[3] = 0x04;
            zipData[4] = 0x0a; //Version
            zipData[5] = 0x00;
            zipData[6] = 0x00; //General purpose flag
            zipData[7] = 0x00;
            zipData[8] = 0x00; //Compression method
            zipData[9] = 0x00;
            zipData[10] = 0x00; //modification time
            zipData[11] = 0x00;
            zipData[12] = 0x00; //modification date
            zipData[13] = 0x00;
            System.arraycopy(dataReceived, totalSize - 12, zipData, 14, 12); //CRC32, compressed size and uncompressed size
            zipData[26] = 0x09; //File name length
            zipData[27] = 0x00;
            zipData[28] = 0x00; //Extra field length
            zipData[29] = 0x00;
            System.arraycopy("a.phyphox".getBytes(), 0, zipData, 30, 9);

            //Data (without data descriptor)
            System.arraycopy(dataReceived, 0, zipData, 39, totalSize-16);

            //Central directory
            zipData[39 + totalSize - 16] = 0x50; //signature
            zipData[39 + totalSize - 16 +  1] = 0x4b;
            zipData[39 + totalSize - 16 +  2] = 0x01;
            zipData[39 + totalSize - 16 +  3] = 0x02;
            zipData[39 + totalSize - 16 +  4] = 0x0a; //Version made by
            zipData[39 + totalSize - 16 +  5] = 0x00;
            zipData[39 + totalSize - 16 +  6] = 0x0a; //Version needed
            zipData[39 + totalSize - 16 +  7] = 0x00;
            zipData[39 + totalSize - 16 +  8] = 0x00; //General purpose flag
            zipData[39 + totalSize - 16 +  9] = 0x00;
            zipData[39 + totalSize - 16 + 10] = 0x00; //Compression method
            zipData[39 + totalSize - 16 + 11] = 0x00;
            zipData[39 + totalSize - 16 + 12] = 0x00; //modification time
            zipData[39 + totalSize - 16 + 13] = 0x00;
            zipData[39 + totalSize - 16 + 14] = 0x00; //modification date
            zipData[39 + totalSize - 16 + 15] = 0x00;
            System.arraycopy(dataReceived, totalSize - 12, zipData, 39 + totalSize - 16 + 16, 12); //CRC32, compressed size and uncompressed size
            zipData[39 + totalSize - 16 + 28] = 0x09; //File name length
            zipData[39 + totalSize - 16 + 29] = 0x00;
            zipData[39 + totalSize - 16 + 30] = 0x00; //Extra field length
            zipData[39 + totalSize - 16 + 31] = 0x00;
            zipData[39 + totalSize - 16 + 32] = 0x00; //File comment length
            zipData[39 + totalSize - 16 + 33] = 0x00;
            zipData[39 + totalSize - 16 + 34] = 0x00; //Disk number
            zipData[39 + totalSize - 16 + 35] = 0x00;
            zipData[39 + totalSize - 16 + 36] = 0x00; //Internal file attributes
            zipData[39 + totalSize - 16 + 37] = 0x00;
            zipData[39 + totalSize - 16 + 38] = 0x00; //External file attributes
            zipData[39 + totalSize - 16 + 39] = 0x00;
            zipData[39 + totalSize - 16 + 40] = 0x00;
            zipData[39 + totalSize - 16 + 41] = 0x00;
            zipData[39 + totalSize - 16 + 42] = 0x00; //Relative offset of local header
            zipData[39 + totalSize - 16 + 43] = 0x00;
            zipData[39 + totalSize - 16 + 44] = 0x00;
            zipData[39 + totalSize - 16 + 45] = 0x00;
            System.arraycopy("a.phyphox".getBytes(), 0, zipData, 39 + totalSize - 16 + 46, 9);

            //End of central directory
            zipData[39 + totalSize - 16 + 55] = 0x50; //signature
            zipData[39 + totalSize - 16 + 55 +  1] = 0x4b;
            zipData[39 + totalSize - 16 + 55 +  2] = 0x05;
            zipData[39 + totalSize - 16 + 55 +  3] = 0x06;
            zipData[39 + totalSize - 16 + 55 +  4] = 0x00; //Disk number
            zipData[39 + totalSize - 16 + 55 +  5] = 0x00;
            zipData[39 + totalSize - 16 + 55 +  6] = 0x00; //Start disk number
            zipData[39 + totalSize - 16 + 55 +  7] = 0x00;
            zipData[39 + totalSize - 16 + 55 +  8] = 0x01; //Number of central directories on disk
            zipData[39 + totalSize - 16 + 55 +  9] = 0x00;
            zipData[39 + totalSize - 16 + 55 + 10] = 0x01; //Number of central directories in total
            zipData[39 + totalSize - 16 + 55 + 11] = 0x00;
            zipData[39 + totalSize - 16 + 55 + 12] = 0x37; //Size of central directory
            zipData[39 + totalSize - 16 + 55 + 13] = 0x00;
            zipData[39 + totalSize - 16 + 55 + 14] = 0x00;
            zipData[39 + totalSize - 16 + 55 + 15] = 0x00;
            zipData[39 + totalSize - 16 + 55 + 16] = (byte) ((long) (39 + totalSize)); //Start of central directory
            zipData[39 + totalSize - 16 + 55 + 17] = (byte) ((long) (39 + totalSize) >> 8);
            zipData[39 + totalSize - 16 + 55 + 18] = (byte) ((long) (39 + totalSize) >> 16);
            zipData[39 + totalSize - 16 + 55 + 19] = (byte) ((long) (39 + totalSize) >> 24);
            zipData[39 + totalSize - 16 + 55 + 20] = 0x00; //Comment length
            zipData[39 + totalSize - 16 + 55 + 21] = 0x00;

        } else {
            zipData = dataReceived;
        }
        return zipData;
    }

    // From Android 15 (SDK 35), because of edge-to-edge UI, there should be inset at status bar
    public static void setWindowInsetListenerForSystemBar(View v){
        ViewCompat.setOnApplyWindowInsetsListener(v, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            }
        });
    }

    public static int getBatteryPercentage(Context context) {

        final int NO_BATTERY_SIGNAL_LEVEL = 0;

        if(context == null) return NO_BATTERY_SIGNAL_LEVEL;

        if (Build.VERSION.SDK_INT >= 21) {
            BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
            if(bm == null) return NO_BATTERY_SIGNAL_LEVEL;

            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, iFilter);
            if(batteryStatus == null) return NO_BATTERY_SIGNAL_LEVEL;

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level == -1 || scale == -1 || scale == 0) return NO_BATTERY_SIGNAL_LEVEL;

            return ( level / scale ) * 100;
        }
    }

    public static int getWifiReceptionStrength(Context context){
        final int MAX_SIGNAL_LEVEL = 5;
        final int NO_CONNECTION_SIGNAL_LEVEL = 0;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return NO_CONNECTION_SIGNAL_LEVEL;

        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (!wifiManager.isWifiEnabled()) return NO_CONNECTION_SIGNAL_LEVEL;

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        if (wifiInfo == null || wifiInfo.getNetworkId() == -1) return NO_CONNECTION_SIGNAL_LEVEL;

        int rssi = wifiInfo.getRssi();

        return WifiManager.calculateSignalLevel(rssi, MAX_SIGNAL_LEVEL);
    }

    public static int getSystemVolume(Context context){
        final int NO_AUDIO_LEVEL = 0;
        if(context == null) return NO_AUDIO_LEVEL;

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if(audioManager == null) return NO_AUDIO_LEVEL;

        int volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return (volumeLevel / maxVolumeLevel) * 100;

    }
}
