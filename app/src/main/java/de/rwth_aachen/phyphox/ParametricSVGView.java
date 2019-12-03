package de.rwth_aachen.phyphox;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import com.caverock.androidsvg.SVG;

import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParametricSVGView extends View {

    Vector<String> svgParts = new Vector<>();
    Vector<Integer> mapping = new Vector<>();
    SVG svg = null;

    float aspectRatio = 1.f;
    int backgroundColor = 0xffffffff;

    ParametricSVGView(Context ctx) {
        super(ctx);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int height, width;

        if (widthMode == MeasureSpec.UNSPECIFIED && heightMode == MeasureSpec.UNSPECIFIED) {
            width = 600;
            height = (int)Math.round(600/aspectRatio);
        } else if (widthMode == MeasureSpec.UNSPECIFIED) {
            height = heightSize;
            width = (int)Math.round(height * aspectRatio);
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            width = widthSize;
            height = (int)Math.round(width / aspectRatio);
        } else {
            width = widthSize;
            height = heightSize;
        }
        setMeasuredDimension(width, height);
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
    }

    public void setSvgParts(final String code) {
        svgParts.clear();
        mapping.clear();

        Pattern p = Pattern.compile("\\[\\[\\[(\\d)\\]\\]\\]");
        Matcher m = p.matcher(code);
        int previous = 0;
        while (m.find()) {
            svgParts.add(code.substring(previous, m.start()));
            mapping.add(Integer.valueOf(m.group(1)));
            previous = m.end();
        }
        svgParts.add(code.substring(previous));

        update(null);
    }

    public void update(double [] values) {
        if ((values == null || values.length == 0) && mapping.size() > 0) {
            svg = null;
            return;
        }

        StringBuilder sb = new StringBuilder();

        Iterator it = svgParts.iterator();

        for (Integer i : mapping) {
            if (i < 1 || i > values.length || Double.isNaN(values[i-1]) || Double.isInfinite(values[i-1])) {
                svg = null;
                return;
            }
            sb.append(it.next());
            sb.append(values[i-1]);
        }
        sb.append(it.next());

        try {
            svg = SVG.getFromString(sb.toString());
        } catch (Exception e) {
            svg = null;
            return;
        }

        aspectRatio = svg.getDocumentAspectRatio();

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(backgroundColor);

        if (svg != null) {
            svg.setDocumentWidth(canvas.getWidth());
            svg.setDocumentHeight(canvas.getHeight());
            svg.renderToCanvas(canvas);
        }
    }

}
