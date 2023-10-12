package de.rwth_aachen.phyphox.Helper;

import android.content.res.Resources;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import de.rwth_aachen.phyphox.R;

public class RGB {
    private static final double HUE_MAX = 360.0;
    int color;

    public RGB(int color) {
        this.color = color | 0xff000000;
    }

    public static RGB fromRGB(int r, int g, int b) {
        return new RGB(0xffffff & ((r << 16) | (g << 8) | b));
    }

    public static RGB fromHexString(String rgb) {
        String[] rgbsep;
        if (rgb.charAt(0) == '#') {
            return new RGB(Integer.parseInt(rgb.substring(1), 16));
        } else {
            rgbsep = rgb.replaceAll("[^\\d,]", "").split(",");
            if (rgbsep.length == 3) {
                return RGB.fromRGB(Integer.parseInt(rgbsep[0]) , Integer.parseInt(rgbsep[1]), Integer.parseInt(rgbsep[2]));
            }
        }
        return new RGB(0x000000);
    }

    public static RGB fromPhyphoxString(String colorStr, Resources res, RGB fallback) {
        if (colorStr == null)
            return fallback;
        //We first check for specific names. As we do not set prefix (like a hash), we have to be careful that these constants do not colide with a valid hex representation of RGB
        switch(colorStr.toLowerCase()) {
            case "orange": return new RGB(res.getColor(R.color.phyphox_primary));
            case "red": return new RGB(res.getColor(R.color.phyphox_red));
            case "magenta": return new RGB(res.getColor(R.color.phyphox_magenta));
            case "blue": return new RGB(res.getColor(R.color.phyphox_blue_60));
            case "green": return new RGB(res.getColor(R.color.phyphox_green));
            case "yellow": return new RGB(res.getColor(R.color.phyphox_yellow));
            case "white": return new RGB(res.getColor(R.color.phyphox_white_100));

            case "weakorange": return new RGB(res.getColor(R.color.phyphox_primary_weak));
            case "weakred": return new RGB(res.getColor(R.color.phyphox_red_weak));
            case "weakmagenta": return new RGB(res.getColor(R.color.phyphox_magenta_weak));
            case "weakblue": return new RGB(res.getColor(R.color.phyphox_blue_40));
            case "weakgreen": return new RGB(res.getColor(R.color.phyphox_green_weak));
            case "weakyellow": return new RGB(res.getColor(R.color.phyphox_yellow_weak));
            case "weakwhite": return new RGB(res.getColor(R.color.phyphox_white_60));
        }

        //Not a constant, so it hast to be hex...
        if (colorStr.startsWith("#"))
            return RGB.fromHexString(colorStr);
        else
            return RGB.fromHexString("#" + colorStr);
    }

    public static RGB fromPhyphoxString(String colorStr, Resources res) {
        return RGB.fromPhyphoxString(colorStr, res, new RGB(0));
    }

    public static RGB fromHSV(double h, double s, double v) {
        int r, g, b, i;
        double f, p, q, t;
        i = (int) Math.floor(h * 6 / HUE_MAX);
        f = h * 6 / HUE_MAX - i;
        p = v * (1 - s);
        q = v * (1 - f * s);
        t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0:
                r = (int) (v * 255);
                g = (int) (t * 255);
                b = (int) (p * 255);
                break;
            case 1:
                r = (int) (q * 255);
                g = (int) (v * 255);
                b = (int) (p * 255);
                break;
            case 2:
                r = (int) (p * 255);
                g = (int) (v * 255);
                b = (int) (t * 255);
                break;
            case 3:
                r = (int) (p * 255);
                g = (int) (q * 255);
                b = (int) (v * 255);
                break;
            case 4:
                r = (int) (t * 255);
                g = (int) (p * 255);
                b = (int) (v * 255);
                break;
            case 5:
                r = (int) (v * 255);
                g = (int) (p * 255);
                b = (int) (q * 255);
                break;
            default:
                return new RGB(0x000000);
        }
        return new RGB(0xFFFFFF & ((r << 16) | (g << 8) | b));
    }

    public int intColor() {
        return color;
    }

    public int b() {
        return color & 0x0000ff;
    }

    public int g() {
        return (color >> 8) & 0x0000ff;
    }

    public int r() {
        return (color >> 16) & 0x0000ff;
    }

    public double hue() {
        int r = r();
        int g = g();
        int b = b();
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        double d = max - min;

        double h;
        if (max == min) {
            h = 0;
        } else if (max == r) {
            h = (g - b + d * (g < b ? 6 : 0)) / (6 * d);
        } else if (max == g) {
            h = (b - r + d * 2) / (6 * d);
        } else {
            h = (r - g + d * 4) / (6 * d);
        }
        return h * HUE_MAX;
    }

    public double saturation() {
        int r = r();
        int g = g();
        int b = b();
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        double d = max - min;
        return (max == 0) ? 0 : d / max;
    }

    public double value() {
        int max = Math.max(r(), Math.max(g(), b()));
        return max/255.0;
    }

    public double luminance() {
        return 0.2126f*(float)Math.pow((r()/255.0+0.055f)/1.055f, 2.4f) + 0.7152f*(float)Math.pow((g()/255.0+0.055f)/1.055f, 2.4f) + 0.0722f*(float)Math.pow((b()/255.0+0.055f)/1.055f, 2.4f);
    }

    public RGB adjustedColorForLightTheme(Resources res) {
        if (color == res.getColor(R.color.phyphox_primary))
            return this;
        if (color == res.getColor(R.color.phyphox_black_60))
            return new RGB(res.getColor(R.color.phyphox_white_100));

        double l = (2.0 - saturation()) * value() / 2.0;
        double s = l > 0 && l < 1 ? saturation() * value() / (l < 0.5 ? l * 2.0 : 2.0 - l * 2.0) : 0.0;
        l = 1.0 - l;
        double t = s * (l < 0.5 ? l : 1.0 - l);
        return RGB.fromHSV(hue(), l > 0 ? 2 * t / (l+t) : 0.0, l+t);
    }

    public RGB autoLightColor(Resources res) {
        if (Helper.isDarkTheme(res)) {
            return this;
        } else {
            return adjustedColorForLightTheme(res);
        }
    }

    public RGB overlayTextColor() {
        if (luminance() > 0.7)
            return new RGB(0x000000);
        else
            return new RGB(0xffffff);
    }
}
