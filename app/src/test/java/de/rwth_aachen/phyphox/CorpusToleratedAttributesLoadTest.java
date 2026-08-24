package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// phyphox-test: corpus-tolerated-attributes-load
//Every file in phyphox-docs' corpus/invalid whose expected.yml entry says "parser: accepts"
//must LOAD successfully: its only defects are unknown or misapplied attributes, which the
//parsers ignore per the unknown-attribute-ignored rule (phyphox-docs spec/rules.yml, decided
//2026-08-24). This pins the compatibility guarantee - a parser that starts rejecting unknown
//attributes breaks files in the wild. Contract: phyphox-docs/corpus/README.md, "The app test
//suites".
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 35)
public class CorpusToleratedAttributesLoadTest {

    private final String relativePath;

    public CorpusToleratedAttributesLoadTest(String relativePath) {
        this.relativePath = relativePath;
    }

    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    public static Collection<Object[]> files() {
        File corpus = CorpusTestEnvironment.findCorpus();
        if (corpus == null) {
            System.err.println("NOTICE: No phyphox-docs checkout found next to this repository - skipping the conformance corpus (corpus-tolerated-attributes-load).");
            return Collections.singletonList(new Object[]{CorpusTestEnvironment.CORPUS_MISSING});
        }
        List<Object[]> parameters = new ArrayList<>();
        try {
            Map<String, String> classification = CorpusTestEnvironment.parserClassification(corpus);
            for (String file : CorpusTestEnvironment.listPhyphoxFiles(corpus, new File(corpus, "invalid")))
                if ("accepts".equals(classification.get(new File(file).getName())))
                    parameters.add(new Object[]{file});
        } catch (Exception e) {
            throw new RuntimeException("Cannot read corpus/invalid/expected.yml", e);
        }
        return parameters;
    }

    @Test
    public void loadsDespiteUnknownAttributes() throws Exception {
        assumeTrue("No phyphox-docs checkout found next to this repository - corpus skipped.",
                !CorpusTestEnvironment.CORPUS_MISSING.equals(relativePath));

        File file = new File(CorpusTestEnvironment.findCorpus(), relativePath);
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(file, activity);
        assertTrue(relativePath + " must load (expected.yml classifies it as \"parser: accepts\": its"
                + " defects are unknown/misapplied attributes, tolerated per unknown-attribute-ignored)"
                + " but failed: " + experiment.message, experiment.loaded);
    }
}
