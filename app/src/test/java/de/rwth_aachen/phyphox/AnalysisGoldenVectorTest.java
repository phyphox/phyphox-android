package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// phyphox-test: analysis-golden-vectors
//Every pair under phyphox-docs' corpus/analysis/vectors: a miniature experiment whose input
//data arrives through container init values, and a statement of what the output buffers must
//hold after a given number of analysis cycles. The experiment is loaded through the real parser
//and never started - the timer case relies on the experiment time being exactly zero - and the
//analysis kernel is driven directly, once per cycle, with cycle numbers 0, 1, 2, ...
//Contract: phyphox-docs/corpus/analysis/README.md, "The runner contract".
//A mismatch is a finding to report back, not something to code around: either the reference
//expectation or this implementation is wrong, and which one is a docs decision.
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 35)
public class AnalysisGoldenVectorTest {

    private static final String VECTORS = "analysis/vectors";

    //Cases whose expectation has been reported to phyphox-docs as wrong and that are skipped
    //with the finding until it is resolved there. Never add a case here because the app
    //disagrees with the reference - a value mismatch is a finding to report, and which side is
    //right is a docs decision. Only a fixture that contradicts itself belongs here.
    private static final Map<String, String> REPORTED_EXPECTATIONS = new LinkedHashMap<String, String>() {{
        put("if/true-branch-array.phyphox",
                "the case declares no comparison attribute, so the condition is false and the "
                        + "output stays untouched (empty) - in the app and in the reference's own "
                        + "if_() alike - but the expectation is the true branch [7, 8, 9]. The "
                        + "case description says less=true; the attribute is missing from if.yml.");
    }};

    private final String relativePath;

    public AnalysisGoldenVectorTest(String relativePath) {
        this.relativePath = relativePath;
    }

    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    public static Collection<Object[]> files() {
        File corpus = CorpusTestEnvironment.findCorpus();
        if (corpus == null) {
            System.err.println("NOTICE: No phyphox-docs checkout found next to this repository - skipping the analysis golden vectors (analysis-golden-vectors).");
            return Collections.singletonList(new Object[]{CorpusTestEnvironment.CORPUS_MISSING});
        }
        List<Object[]> parameters = new ArrayList<>();
        for (String file : CorpusTestEnvironment.listPhyphoxFiles(corpus, new File(corpus, VECTORS)))
            parameters.add(new Object[]{file});
        if (parameters.isEmpty()) {
            //A phyphox-docs checkout from before the vectors existed. Skip visibly rather than
            //handing the runner an empty parameter list, which it cannot start with at all.
            System.err.println("NOTICE: The phyphox-docs checkout next to this repository carries no " + VECTORS + " - skipping the analysis golden vectors (analysis-golden-vectors).");
            return Collections.singletonList(new Object[]{CorpusTestEnvironment.CORPUS_MISSING});
        }
        return parameters;
    }

    @Test
    public void matchesGoldenVector() throws Exception {
        assumeTrue("No phyphox-docs checkout found next to this repository - vectors skipped.",
                !CorpusTestEnvironment.CORPUS_MISSING.equals(relativePath));

        File corpus = CorpusTestEnvironment.findCorpus();
        File file = new File(corpus, relativePath);
        int[] declared = CorpusTestEnvironment.declaredVersion(file);
        if (declared != null)
            assumeTrue("Declares format version " + declared[0] + "." + declared[1]
                            + " > supported " + PhyphoxFile.phyphoxFileVersion + " - skipped.",
                    CorpusTestEnvironment.versionAtMostSupported(declared));

        for (Map.Entry<String, String> reported : REPORTED_EXPECTATIONS.entrySet())
            assumeTrue("Expectation reported to phyphox-docs: " + reported.getValue(),
                    !relativePath.endsWith(reported.getKey()));

        JSONObject expected = readJson(new File(corpus,
                relativePath.substring(0, relativePath.length() - ".phyphox".length()) + ".expected.json"));

        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
                new ByteArrayInputStream(withMinimalView(file)), activity);
        assertTrue(relativePath + " failed to load: " + experiment.message, experiment.loaded);

        //The expectations, indexed by the 1-based count of executed cycles they apply after.
        Map<Integer, JSONObject> afterCycle = new LinkedHashMap<>();
        JSONArray expects = expected.getJSONArray("expect");
        for (int i = 0; i < expects.length(); i++)
            afterCycle.put(expects.getJSONObject(i).getInt("after_cycle"),
                    expects.getJSONObject(i).getJSONObject("buffers"));

        double[] defaultTolerance = tolerance(expected.getJSONObject("default_tolerance"), new double[]{0.0, 0.0});

        List<String> findings = new ArrayList<>();
        int cycles = expected.getInt("cycles");
        for (int cycle = 0; cycle < cycles; cycle++) {
            runAnalysisCycle(experiment, cycle);

            JSONObject buffers = afterCycle.get(cycle + 1);
            if (buffers == null)
                continue;
            for (Iterator<String> names = buffers.keys(); names.hasNext(); ) {
                String name = names.next();
                compare(findings, experiment, cycle + 1, name, buffers.getJSONObject(name), defaultTolerance);
            }
        }

        if (!findings.isEmpty())
            fail(expected.getString("module") + "/" + expected.getString("case") + ": "
                    + String.join("; ", findings));
    }

    //TEMPORARY, reported to phyphox-docs: the generated vectors declare no views at all, but
    //both parsers refuse such a file - Android with "No valid view found", iOS with
    //missingElement("view") - so nothing would load. Until the generator emits a views block,
    //the runner adds an empty one; it does not touch anything the vectors are about, since a
    //view only displays buffers and is never rendered here. Remove this once the vectors carry
    //a view of their own.
    private static byte[] withMinimalView(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        if (content.contains("<views"))
            return content.getBytes(StandardCharsets.UTF_8);
        //A view needs a name and at least one element to count on Android, and a separator is
        //the only element that displays no buffer at all.
        return content.replace("</phyphox>", "<views><view label=\"Data\"><separator/></view></views></phyphox>")
                .getBytes(StandardCharsets.UTF_8);
    }

    //One analysis pass, the way PhyphoxExperiment.processAnalysis runs it: the module loop in
    //document order, each module deciding by its cycles attribute whether it runs at all. What
    //is deliberately left out is everything the scheduling layer above the kernel does - sleep,
    //dynamicSleep, onUserInput and requireFill must not gate these runs - and the sensor, audio
    //and network plumbing that a vector experiment does not have.
    private static void runAnalysisCycle(PhyphoxExperiment experiment, int cycle) {
        experiment.dataLock.lock();
        try {
            experiment.analysisTime = experiment.experimentTimeReference.getExperimentTime();
            experiment.analysisLinearTime = experiment.experimentTimeReference.getLinearTime();
            for (Analysis.AnalysisModule module : experiment.analysis)
                module.updateIfNotStatic(cycle);
        } finally {
            experiment.dataLock.unlock();
        }
    }

    private void compare(List<String> findings, PhyphoxExperiment experiment, int cycle,
                         String name, JSONObject spec, double[] defaultTolerance) throws JSONException {
        DataBuffer buffer = experiment.getBuffer(name);
        assertNotNull(relativePath + ": buffer \"" + name + "\" does not exist", buffer);

        double[] t = tolerance(spec, defaultTolerance);
        JSONArray values = spec.getJSONArray("values");
        Double[] actual = buffer.getArray();

        if (actual.length != values.length()) {
            findings.add("after cycle " + cycle + ", buffer \"" + name + "\": expected "
                    + values.length() + " values " + describe(values) + ", got " + actual.length
                    + " " + describe(actual));
            return;
        }
        for (int i = 0; i < actual.length; i++) {
            double want = expectedValue(values.get(i));
            if (!matches(want, actual[i], t[0], t[1]))
                findings.add("after cycle " + cycle + ", buffer \"" + name + "\"[" + i
                        + "]: expected " + want + ", got " + actual[i]);
        }
    }

    //{abs, rel}, taking whatever the object overrides and the fallback for the rest. The
    //generated files carry the numbers as JSON numbers or as strings ("1e-05"), so read them
    //through getDouble, which accepts both.
    private static double[] tolerance(JSONObject spec, double[] fallback) throws JSONException {
        return new double[]{
                spec.has("abs") ? spec.getDouble("abs") : fallback[0],
                spec.has("rel") ? spec.getDouble("rel") : fallback[1]};
    }

    //Non-finite expected values are the strings "nan", "inf" and "-inf".
    private static double expectedValue(Object raw) {
        if (raw instanceof String) {
            String value = ((String) raw).trim().toLowerCase(Locale.US);
            switch (value) {
                case "nan":
                    return Double.NaN;
                case "inf":
                case "+inf":
                    return Double.POSITIVE_INFINITY;
                case "-inf":
                    return Double.NEGATIVE_INFINITY;
                default:
                    return Double.parseDouble(value);
            }
        }
        return ((Number) raw).doubleValue();
    }

    private static boolean matches(double expected, double actual, double abs, double rel) {
        if (Double.isNaN(expected))
            return Double.isNaN(actual);
        if (Double.isInfinite(expected))
            return Double.isInfinite(actual) && (expected > 0) == (actual > 0);
        if (Double.isNaN(actual) || Double.isInfinite(actual))
            return false;
        return Math.abs(actual - expected) <= abs + rel * Math.abs(expected);
    }

    private static String describe(JSONArray values) throws JSONException {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length(); i++)
            sb.append(i > 0 ? ", " : "").append(expectedValue(values.get(i)));
        return sb.append("]").toString();
    }

    private static String describe(Double[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++)
            sb.append(i > 0 ? ", " : "").append(values[i]);
        return sb.append("]").toString();
    }

    private static JSONObject readJson(File file) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }
}
