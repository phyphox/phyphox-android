package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

//An export set holds as many rows as its LONGEST column, with the shorter columns padded with
//NaN (ruled 2026-08-25, export-set-row-count). Sizing by the first column instead truncated
//every longer column, and an empty first container dropped the whole set - motion_stopwatch
//exported zero rows for its "All" set although one of its two containers had data.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ExportRowCountTest {

    //The first container stays empty, the second fills: the case that used to export nothing.
    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Export row count</title>"
                    + "<category>Test</category>"
                    + "<description>Two export columns of different length.</description>"
                    + "<data-containers>"
                    + "<container size=\"8\">empty</container>"
                    + "<container size=\"8\" init=\"1,2,3\">filled</container>"
                    + "<container size=\"8\" init=\"7,8\">shorter</container>"
                    + "</data-containers>"
                    + "<views><view label=\"View\"><value label=\"v\"><input>filled</input></value></view></views>"
                    + "<export>"
                    + "<set name=\"All\">"
                    + "<data name=\"a\">empty</data>"
                    + "<data name=\"b\">filled</data>"
                    + "</set>"
                    + "<set name=\"Mixed\">"
                    + "<data name=\"c\">filled</data>"
                    + "<data name=\"d\">shorter</data>"
                    + "</set>"
                    + "</export>"
                    + "</phyphox>";

    private PhyphoxExperiment load(Experiment activity) {
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
                new ByteArrayInputStream(EXPERIMENT.getBytes(StandardCharsets.UTF_8)), activity);
        assertTrue("Test experiment failed to load: " + experiment.message, experiment.loaded);
        return experiment;
    }

    //The rows of one .csv inside the exported zip, without the header.
    private List<String> csvRows(File zip, String set) throws Exception {
        try (ZipFile archive = new ZipFile(zip)) {
            String content = new String(readAll(archive, set + ".csv"), StandardCharsets.UTF_8);
            List<String> rows = new ArrayList<>();
            String[] lines = content.split("\n");
            for (int i = 1; i < lines.length; i++)
                if (!lines[i].trim().isEmpty())
                    rows.add(lines[i].trim());
            return rows;
        }
    }

    private byte[] readAll(ZipFile archive, String entry) throws Exception {
        java.io.InputStream in = archive.getInputStream(archive.getEntry(entry));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1)
            out.write(buffer, 0, n);
        in.close();
        return out.toByteArray();
    }

    @Test
    public void csvHoldsAsManyRowsAsTheLongestColumn() throws Exception {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = load(activity);

        File file = experiment.exporter.exportDirect(experiment.exporter.exportFormats[1],
                activity.getCacheDir(), false, "rowcount", activity);

        List<String> all = csvRows(file, "All");
        assertEquals("The filled column decides, not the empty first one", 3, all.size());
        assertTrue("The empty column is padded: " + all.get(0), all.get(0).startsWith("NaN,"));
        assertTrue("The filled column keeps its values: " + all.get(2),
                all.get(2).endsWith("3.000000000E0"));

        List<String> mixed = csvRows(file, "Mixed");
        assertEquals("A shorter later column does not shorten the set", 3, mixed.size());
        assertTrue("The shorter column is padded: " + mixed.get(2), mixed.get(2).endsWith("NaN"));
    }

    @Test
    public void xlsxHoldsAsManyRowsAsTheLongestColumn() throws Exception {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = load(activity);

        File file = experiment.exporter.exportDirect(experiment.exporter.exportFormats[0],
                activity.getCacheDir(), false, "rowcount", activity);

        try (ZipFile archive = new ZipFile(file)) {
            //Sheet 1 is the first export set; a header row plus one row per value.
            Document sheet = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(readAll(archive, "xl/worksheets/sheet1.xml")));
            assertEquals("Header plus one row per value of the longest column",
                    4, sheet.getElementsByTagName("row").getLength());
        }
    }

    //The file the ruling came from, so the fix cannot silently regress on the real thing.
    @Test
    public void theShippedMotionStopwatchExportsItsIntervals() throws Exception {
        File asset = new File("src/main/assets/experiments/motion_stopwatch.phyphox");
        if (!asset.isFile())
            asset = new File("app/src/main/assets/experiments/motion_stopwatch.phyphox");
        org.junit.Assume.assumeTrue("The experiment collection submodule is not checked out",
                asset.isFile());

        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(asset, activity);
        assertTrue(experiment.message, experiment.loaded);

        //"All" maps tlist (empty until something is timed) and dtlist (initialized), so the set
        //used to export nothing at all.
        DataBuffer intervals = experiment.getBuffer("dtlist");
        experiment.dataLock.lock();
        try {
            intervals.append(1.5);
            intervals.append(2.5);
        } finally {
            experiment.dataLock.unlock();
        }

        File file = experiment.exporter.exportDirect(experiment.exporter.exportFormats[1],
                activity.getCacheDir(), false, "motion", activity);
        assertEquals("The intervals must reach the file even with tlist empty",
                2, csvRows(file, "All").size());
    }
}
