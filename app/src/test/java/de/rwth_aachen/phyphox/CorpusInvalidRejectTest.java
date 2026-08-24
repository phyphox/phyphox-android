package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

// phyphox-test: corpus-invalid-reject
//Every file in phyphox-docs' corpus/invalid whose expected.yml entry says "parser: rejects"
//must fail to load. Any error is acceptable; error message texts are platform wording and are
//not asserted (the defects themselves are documented in corpus/invalid/expected.yml). Files
//classified "parser: accepts" are covered by CorpusToleratedAttributesLoadTest instead.
//Contract: phyphox-docs/corpus/README.md, "The app test suites".
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 35)
public class CorpusInvalidRejectTest {

    private final String relativePath;

    public CorpusInvalidRejectTest(String relativePath) {
        this.relativePath = relativePath;
    }

    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    public static Collection<Object[]> files() {
        File corpus = CorpusTestEnvironment.findCorpus();
        if (corpus == null) {
            System.err.println("NOTICE: No phyphox-docs checkout found next to this repository - skipping the conformance corpus (corpus-invalid-reject).");
            return Collections.singletonList(new Object[]{CorpusTestEnvironment.CORPUS_MISSING});
        }
        List<Object[]> parameters = new ArrayList<>();
        try {
            Map<String, String> classification = CorpusTestEnvironment.parserClassification(corpus);
            for (String file : CorpusTestEnvironment.listPhyphoxFiles(corpus, new File(corpus, "invalid")))
                //Unclassified files run here too, so a missing expected.yml entry fails visibly
                //instead of the file silently not being tested at all.
                if (!"accepts".equals(classification.get(new File(file).getName())))
                    parameters.add(new Object[]{file});
        } catch (Exception e) {
            throw new RuntimeException("Cannot read corpus/invalid/expected.yml", e);
        }
        return parameters;
    }

    @Test
    public void isRejected() throws Exception {
        assumeTrue("No phyphox-docs checkout found next to this repository - corpus skipped.",
                !CorpusTestEnvironment.CORPUS_MISSING.equals(relativePath));

        File corpus = CorpusTestEnvironment.findCorpus();
        String classification = CorpusTestEnvironment.parserClassification(corpus)
                .get(new File(relativePath).getName());
        assertNotNull(relativePath + " has no \"parser:\" classification in corpus/invalid/expected.yml",
                classification);

        File file = new File(corpus, relativePath);
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(file, activity);
        assertFalse(relativePath + " loaded although expected.yml classifies it as \"parser: rejects\"",
                experiment.loaded);
    }
}
