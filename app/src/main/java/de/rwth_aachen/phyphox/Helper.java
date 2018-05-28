package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

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
}
