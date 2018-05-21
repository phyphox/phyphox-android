package de.rwth_aachen.phyphox;

import android.content.res.Resources;

public abstract class Helper {
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
}
