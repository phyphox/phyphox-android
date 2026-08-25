package de.rwth_aachen.phyphox;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

//Golden-image plumbing for the view snapshot suite: render one view element to a bitmap and
//compare it against the recorded PNG, or record it when asked.
//
//Goldens live in this repository (renderer output is platform-specific by nature - see
//phyphox-docs fixtures/views/README.md, "The snapshot contract") under
//app/src/test/goldens/views/<fixture>/<element>/<configuration>.png.
//
//Recording: ./gradlew testRegularDebugUnitTest -Pphyphox.goldens=record
//A recorded golden is reviewed like any other change - it is the reference for every later run.
final class ViewGolden {

    private static final File ROOT = goldenRoot();

    private ViewGolden() {
    }

    static boolean recording() {
        return "record".equals(System.getProperty("phyphox.goldens"));
    }

    //app/src/test/goldens, wherever the tests happen to run from.
    private static File goldenRoot() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int i = 0; i < 4 && dir != null; i++) {
            File candidate = new File(dir, "app/src/test/goldens");
            if (new File(dir, "app/src/test").isDirectory())
                return candidate;
            if (new File(dir, "src/test").isDirectory())
                return new File(dir, "src/test/goldens");
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("Cannot locate the golden directory");
    }

    //A file name that still reads like the element it shows: "precision 6" -> "precision-6".
    static String slug(String name) {
        String slug = (name == null ? "unnamed" : name).toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "unnamed" : slug;
    }

    //Draw a view exactly as wide as the screen it belongs to, as tall as it wants to be, on the
    //window background it sits on. Without the background a dark-theme element is white text on
    //transparent - a golden nobody can review.
    static Bitmap render(View view, int widthPx, int backgroundColor) {
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = Math.max(view.getMeasuredHeight(), 1);
        view.layout(0, 0, widthPx, height);

        Bitmap bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(backgroundColor);
        view.draw(canvas);
        return bitmap;
    }

    //Compares against the recorded golden. Returns null when they match, else what differs.
    static String compare(Bitmap actual, String fixture, String element, String configuration)
            throws IOException {
        File golden = new File(new File(new File(ROOT, "views"), fixture + "/" + element),
                configuration + ".png");

        if (recording()) {
            write(actual, golden);
            return null;
        }
        if (!golden.isFile())
            return "no golden recorded yet at " + relative(golden)
                    + " - record it with -Pphyphox.goldens=record and review the image";

        Bitmap expected = android.graphics.BitmapFactory.decodeFile(golden.getAbsolutePath());
        if (expected == null)
            return "the golden at " + relative(golden) + " is not a readable image";
        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            writeFailure(actual, golden);
            return "size " + actual.getWidth() + "x" + actual.getHeight() + " does not match the "
                    + "golden's " + expected.getWidth() + "x" + expected.getHeight()
                    + " (actual written next to it)";
        }

        long differing = 0;
        int firstX = -1, firstY = -1;
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                if (expected.getPixel(x, y) != actual.getPixel(x, y)) {
                    if (differing == 0) {
                        firstX = x;
                        firstY = y;
                    }
                    differing++;
                }
            }
        }
        if (differing == 0)
            return null;

        writeFailure(actual, golden);
        return differing + " of " + ((long) actual.getWidth() * actual.getHeight())
                + " pixels differ, first at " + firstX + "," + firstY
                + " (actual written next to the golden)";
    }

    private static void writeFailure(Bitmap actual, File golden) throws IOException {
        write(actual, new File(golden.getParentFile(),
                golden.getName().replace(".png", ".actual.png")));
    }

    private static void write(Bitmap bitmap, File file) throws IOException {
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs())
            throw new IOException("Cannot create " + file.getParentFile());
        try (OutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static String relative(File file) {
        String root = new File(System.getProperty("user.dir")).getAbsolutePath();
        String path = file.getAbsolutePath();
        return path.startsWith(root) ? path.substring(root.length() + 1) : path;
    }
}
