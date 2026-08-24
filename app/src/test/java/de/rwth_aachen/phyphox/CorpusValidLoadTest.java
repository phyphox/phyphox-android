package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertFalse;
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

// phyphox-test: corpus-valid-load
//Every file in phyphox-docs' corpus/valid and corpus/generated whose declared format version is
//at most PhyphoxFile.phyphoxFileVersion must load through the real loading path without error.
//Files declaring a newer version are skipped, not failed - they exist for future format
//versions. A file with an entry in its directory's expected.yml exercises a construct with a
//deliberate platform difference: if that entry maps android to "rejects", the refusal is
//asserted instead of the load. Contract: phyphox-docs/corpus/README.md, "The app test suites".
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 35)
public class CorpusValidLoadTest {

    private final String relativePath;

    public CorpusValidLoadTest(String relativePath) {
        this.relativePath = relativePath;
    }

    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    public static Collection<Object[]> files() {
        File corpus = CorpusTestEnvironment.findCorpus();
        if (corpus == null) {
            System.err.println("NOTICE: No phyphox-docs checkout found next to this repository - skipping the conformance corpus (corpus-valid-load).");
            return Collections.singletonList(new Object[]{CorpusTestEnvironment.CORPUS_MISSING});
        }
        List<Object[]> parameters = new ArrayList<>();
        for (String subdir : new String[]{"valid", "generated"})
            for (String file : CorpusTestEnvironment.listPhyphoxFiles(corpus, new File(corpus, subdir)))
                parameters.add(new Object[]{file});
        return parameters;
    }

    @Test
    public void matchesExpectedLoadResult() throws Exception {
        assumeTrue("No phyphox-docs checkout found next to this repository - corpus skipped.",
                !CorpusTestEnvironment.CORPUS_MISSING.equals(relativePath));

        File corpus = CorpusTestEnvironment.findCorpus();
        File file = new File(corpus, relativePath);
        int[] declared = CorpusTestEnvironment.declaredVersion(file);
        if (declared != null)
            assumeTrue("Declares format version " + declared[0] + "." + declared[1]
                            + " > supported " + PhyphoxFile.phyphoxFileVersion + " - skipped.",
                    CorpusTestEnvironment.versionAtMostSupported(declared));

        String expectation = CorpusTestEnvironment.androidExpectation(corpus, relativePath);

        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(file, activity);

        if ("rejects".equals(expectation))
            //A recorded platform difference: the refusal is contract, so assert it rather than
            //excusing the file from the corpus.
            assertFalse(relativePath + " loaded although expected.yml maps android to \"rejects\"",
                    experiment.loaded);
        else
            assertTrue(relativePath + " failed to load: " + experiment.message, experiment.loaded);
    }
}
