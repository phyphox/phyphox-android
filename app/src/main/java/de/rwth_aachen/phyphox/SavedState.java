package de.rwth_aachen.phyphox;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.rwth_aachen.phyphox.helper.Helper;

//The saved-state container of phyphox-docs docs/saved-states.md (phyphox 1.3.0): experiment.phyphox
//byte for byte as loaded, res/ with the referenced resources, data/index.csv plus one file of
//little-endian binary64 values per container, meta/device.csv, meta/time.csv and meta/state.csv in
//a fixed CSV dialect (comma, dot, quoted strings, LF). Shared as a zip; in the collection the same
//tree is kept extracted in a <uuid>.phystate directory, so the list reads experiment.phyphox like
//any other file and a rename touches meta/state.csv only.
public class SavedState {
    public static final String DIRECTORY_SUFFIX = ".phystate";
    public static final String EXPERIMENT_FILE = "experiment.phyphox";
    public static final String STATE_CSV = "meta/state.csv";
    public static final String TIME_CSV = "meta/time.csv";
    public static final String DEVICE_CSV = "meta/device.csv";
    public static final String INDEX_CSV = "data/index.csv";
    public static final int FORMAT = 1;

    //The directory an experiment file is loaded as a state from: its parent if that holds meta/state.csv
    public static File folderOf(File experimentFile) {
        File parent = experimentFile.getAbsoluteFile().getParentFile();
        if (parent != null && new File(parent, STATE_CSV).isFile())
            return parent;
        return null;
    }

    public static String folderPathOf(File experimentFile) {
        File folder = folderOf(experimentFile);
        return folder == null ? null : folder.getAbsolutePath();
    }

    //The .phystate directory of a collection entry (xmlFile relative to filesDir), or null for a plain file
    public static File directoryOf(File filesDir, String xmlFile) {
        File parent = new File(filesDir, xmlFile).getParentFile();
        if (parent != null && parent.getName().endsWith(DIRECTORY_SUFFIX))
            return parent;
        return null;
    }

    public static String readTitle(File dir) {
        try {
            return readProperties(new File(dir, STATE_CSV)).get("title");
        } catch (IOException e) {
            return null;
        }
    }

    /* ---------- writing ---------- */

    interface Content {
        void writeTo(OutputStream os) throws IOException;
    }

    interface Sink {
        void put(String path, Content content) throws IOException;
    }

    public static void writeZip(PhyphoxExperiment experiment, String title, Context ctx, OutputStream os) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(os);
        write(experiment, title, ctx, (path, content) -> {
            zip.putNextEntry(new ZipEntry(path));
            content.writeTo(zip);
            zip.closeEntry();
        });
        zip.finish();
    }

    public static void writeDirectory(PhyphoxExperiment experiment, String title, Context ctx, File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs())
            throw new IOException("Could not create " + dir.getName() + ".");
        write(experiment, title, ctx, (path, content) -> {
            File file = new File(dir, path);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                throw new IOException("Could not create " + path + ".");
            try (FileOutputStream out = new FileOutputStream(file)) {
                content.writeTo(out);
            }
        });
    }

    static void write(PhyphoxExperiment experiment, String title, Context ctx, Sink sink) throws IOException {
        if (experiment.source == null)
            throw new IOException("Source is null.");

        sink.put(EXPERIMENT_FILE, os -> os.write(experiment.source));

        //Only what the experiment references; a resource that is neither in its folder nor bundled is left out
        for (String name : experiment.resources) {
            if (!Helper.isSafeResourceName(name))
                continue;
            File file = experiment.resourceFolder == null ? null : new File(experiment.resourceFolder, name);
            InputStream in;
            if (file != null && file.isFile()) {
                in = new FileInputStream(file);
            } else {
                try {
                    in = ctx.getAssets().open("experiments/res/" + name);
                } catch (IOException e) {
                    continue;
                }
            }
            try {
                final InputStream source = in;
                sink.put("res/" + name, os -> copy(source, os));
            } finally {
                in.close();
            }
        }

        //Snapshot every container in one go, under the lock like every other reader
        List<DataBuffer> buffers = new ArrayList<>(experiment.dataBuffers);
        Double[][] values = new Double[buffers.size()][];
        experiment.dataLock.lock();
        try {
            for (int i = 0; i < buffers.size(); i++)
                values[i] = buffers.get(i).getArray();
        } finally {
            experiment.dataLock.unlock();
        }

        String[] files = new String[buffers.size()];
        Set<String> used = new HashSet<>();
        StringBuilder index = new StringBuilder("\"container\",\"file\",\"count\"\n");
        for (int i = 0; i < buffers.size(); i++) {
            String base = entryName(buffers.get(i).name);
            String candidate = base + ".bin";
            for (int k = 2; used.contains(candidate); k++)
                candidate = base + "-" + k + ".bin";
            used.add(candidate);
            files[i] = candidate;
            index.append(csvString(buffers.get(i).name)).append(',').append(csvString(candidate)).append(',').append(values[i].length).append('\n');
        }
        sink.put(INDEX_CSV, os -> os.write(index.toString().getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < buffers.size(); i++) {
            final Double[] data = values[i];
            sink.put("data/" + files[i], os -> {
                ByteBuffer bytes = ByteBuffer.allocate(8 * data.length).order(ByteOrder.LITTLE_ENDIAN);
                for (Double v : data)
                    bytes.putDouble(v == null ? Double.NaN : v);
                os.write(bytes.array());
            });
        }

        sink.put(DEVICE_CSV, os -> os.write(DataExport.deviceCsv(',', ctx).getBytes(StandardCharsets.UTF_8)));
        sink.put(TIME_CSV, os -> os.write(DataExport.timeCsv(experiment.experimentTimeReference, ',', '.').getBytes(StandardCharsets.UTF_8)));

        long now = System.currentTimeMillis();
        String state = "\"property\",\"value\"\n"
                + "\"format\"," + csvString(Integer.toString(FORMAT)) + "\n"
                + "\"title\"," + csvString(title) + "\n"
                + "\"saved\"," + csvString(secondsFormat().format(now / 1000.)) + "\n"
                + "\"saved text\"," + csvString(DataExport.dateFormat().format(now)) + "\n"
                + "\"app\"," + csvString("phyphox " + BuildConfig.VERSION_NAME + " (Android)") + "\n";
        sink.put(STATE_CSV, os -> os.write(state.getBytes(StandardCharsets.UTF_8)));
    }

    //The writer rule of the docs page: ASCII letters, digits, _ and - kept, everything else to _, 64 characters
    static String entryName(String bufferName) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < bufferName.length() && sb.length() < 64) {
            int cp = bufferName.codePointAt(i);
            i += Character.charCount(cp);
            boolean keep = (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9') || cp == '_' || cp == '-';
            sb.append(keep ? (char) cp : '_');
        }
        return sb.toString();
    }

    static DecimalFormat secondsFormat() {
        DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
        format.applyPattern("############0.000");
        DecimalFormatSymbols dfs = format.getDecimalFormatSymbols();
        dfs.setDecimalSeparator('.');
        format.setDecimalFormatSymbols(dfs);
        format.setGroupingUsed(false);
        return format;
    }

    static String csvString(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1)
            out.write(buffer, 0, n);
    }

    //Zips an extracted state tree (for sharing an entry of the collection)
    public static void zipDirectory(File dir, File zipFile) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipTree(zip, dir, "");
        }
    }

    private static void zipTree(ZipOutputStream zip, File dir, String prefix) throws IOException {
        File[] entries = dir.listFiles();
        if (entries == null)
            return;
        java.util.Arrays.sort(entries);
        for (File entry : entries) {
            if (entry.isDirectory()) {
                zipTree(zip, entry, prefix + entry.getName() + "/");
            } else {
                zip.putNextEntry(new ZipEntry(prefix + entry.getName()));
                try (FileInputStream in = new FileInputStream(entry)) {
                    copy(in, zip);
                }
                zip.closeEntry();
            }
        }
    }

    //Renaming rewrites the title of meta/state.csv and nothing else
    public static void rename(File dir, String title) throws IOException {
        File csv = new File(dir, STATE_CSV);
        List<String[]> rows = readCsv(csv);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (i > 0 && row.length >= 2 && row[0].equals("title"))
                row[1] = title;
            for (int j = 0; j < row.length; j++) {
                if (j > 0)
                    sb.append(',');
                sb.append(csvString(row[j]));
            }
            sb.append('\n');
        }
        try (FileOutputStream out = new FileOutputStream(csv)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /* ---------- reading ---------- */

    //Null if the tree is a loadable state, otherwise the message for the user. A container with
    //meta/state.csv but without the other required files, an unknown format or a data file whose
    //size does not match its count is refused as a whole.
    public static String validate(File dir) {
        File stateCsv = new File(dir, STATE_CSV);
        if (!stateCsv.isFile())
            return "This is not a saved state: " + STATE_CSV + " is missing.";
        Map<String, String> props;
        try {
            props = readProperties(stateCsv);
        } catch (IOException e) {
            return "Could not read " + STATE_CSV + ": " + e.getMessage();
        }
        String format = props.get("format");
        if (format == null)
            return "The saved state does not declare a format.";
        int formatNumber;
        try {
            formatNumber = Integer.parseInt(format.trim());
        } catch (NumberFormatException e) {
            return "The saved state has an invalid format \"" + format + "\".";
        }
        if (formatNumber != FORMAT)
            return "This saved state uses format " + formatNumber + ", which this version of phyphox does not support. Please update phyphox.";
        if (props.get("title") == null)
            return "The saved state has no title.";
        File timeCsv = new File(dir, TIME_CSV);
        if (!timeCsv.isFile())
            return "The saved state is incomplete: " + TIME_CSV + " is missing.";
        File indexCsv = new File(dir, INDEX_CSV);
        if (!indexCsv.isFile())
            return "The saved state is incomplete: " + INDEX_CSV + " is missing.";

        try {
            List<String[]> rows = readCsv(indexCsv);
            File dataDir = new File(dir, "data");
            String dataPath = dataDir.getCanonicalPath() + File.separator;
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < 3)
                    return "The saved state is damaged: incomplete row in " + INDEX_CSV + ".";
                long count;
                try {
                    count = Long.parseLong(row[2].trim());
                } catch (NumberFormatException e) {
                    return "The saved state is damaged: invalid count for container \"" + row[0] + "\".";
                }
                if (!Helper.isSafeResourceName(row[1]))
                    return "The saved state is damaged: invalid data file name for container \"" + row[0] + "\".";
                File data = new File(dataDir, row[1]);
                if (!data.getCanonicalPath().startsWith(dataPath))
                    return "The saved state is damaged: invalid data file name for container \"" + row[0] + "\".";
                if (!data.isFile())
                    return "The saved state is damaged: the data file of container \"" + row[0] + "\" is missing.";
                if (data.length() != count * 8)
                    return "The saved state is damaged: the data of container \"" + row[0] + "\" does not match its count.";
            }
            rows = readCsv(timeCsv);
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < 3)
                    return "The saved state is damaged: incomplete row in " + TIME_CSV + ".";
                try {
                    parseNumber(row[1]);
                    parseNumber(row[2]);
                } catch (NumberFormatException e) {
                    return "The saved state is damaged: invalid time in " + TIME_CSV + ".";
                }
            }
        } catch (IOException e) {
            return "Could not read the saved state: " + e.getMessage();
        }
        return null;
    }

    //Applies the state to a freshly parsed experiment (docs page, "Loading semantics"): buffers are
    //replaced by their files, a static buffer with data is marked filled, the time reference is
    //rebuilt from meta/time.csv and the title taken from meta/state.csv. Null on success.
    public static String restore(PhyphoxExperiment experiment, File dir) {
        String error = validate(dir);
        if (error != null)
            return error;
        try {
            List<String[]> rows = readCsv(new File(dir, INDEX_CSV));
            experiment.dataLock.lock();
            try {
                for (int i = 1; i < rows.size(); i++) {
                    String[] row = rows.get(i);
                    DataBuffer buffer = experiment.getBuffer(row[0]);
                    if (buffer == null)
                        continue;
                    buffer.restore(readDoubles(new File(new File(dir, "data"), row[1])));
                }
            } finally {
                experiment.dataLock.unlock();
            }

            experiment.experimentTimeReference.reset();
            rows = readCsv(new File(dir, TIME_CSV));
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                ExperimentTimeReference.TimeMappingEvent event;
                switch (row[0].trim().toUpperCase(Locale.ROOT)) {
                    case "START": event = ExperimentTimeReference.TimeMappingEvent.START; break;
                    case "PAUSE": event = ExperimentTimeReference.TimeMappingEvent.PAUSE; break;
                    default: continue;
                }
                experiment.experimentTimeReference.addRestoredMapping(event, parseNumber(row[1]), Math.round(parseNumber(row[2]) * 1000.0));
            }

            experiment.stateTitle = readProperties(new File(dir, STATE_CSV)).get("title");
        } catch (IOException | NumberFormatException e) {
            return "Could not read the saved state: " + e.getMessage();
        }
        return null;
    }

    private static Double[] readDoubles(File file) throws IOException {
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
            copy(in, out);
            bytes = out.toByteArray();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        Double[] values = new Double[bytes.length / 8];
        for (int i = 0; i < values.length; i++)
            values[i] = buffer.getDouble();
        return values;
    }

    //Plain and scientific notation, NaN and the infinities in any case
    static double parseNumber(String s) throws NumberFormatException {
        String t = s.trim();
        switch (t.toLowerCase(Locale.ROOT)) {
            case "nan": return Double.NaN;
            case "inf": case "+inf": case "infinity": case "+infinity": return Double.POSITIVE_INFINITY;
            case "-inf": case "-infinity": return Double.NEGATIVE_INFINITY;
        }
        return Double.parseDouble(t);
    }

    static Map<String, String> readProperties(File csv) throws IOException {
        Map<String, String> props = new LinkedHashMap<>();
        List<String[]> rows = readCsv(csv);
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).length >= 2)
                props.put(rows.get(i)[0], rows.get(i)[1]);
        }
        return props;
    }

    static List<String[]> readCsv(File csv) throws IOException {
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(csv)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            bytes = out.toByteArray();
        }
        return parseCsv(new String(bytes, StandardCharsets.UTF_8));
    }

    //The fixed dialect: comma, double-quoted strings with doubled inner quotes, LF (CRLF tolerated)
    static List<String[]> parseCsv(String text) {
        List<String[]> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int i = (!text.isEmpty() && text.charAt(0) == (char) 0xFEFF) ? 1 : 0; //a UTF-8 BOM, which a spreadsheet may write
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                } else
                    field.append(c);
                i++;
                continue;
            }
            if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                fields.add(field.toString());
                field.setLength(0);
                if (fields.size() > 1 || !fields.get(0).isEmpty())
                    rows.add(fields.toArray(new String[0]));
                fields.clear();
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n')
                    i++;
            } else
                field.append(c);
            i++;
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            rows.add(fields.toArray(new String[0]));
        }
        return rows;
    }
}
