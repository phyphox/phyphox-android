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

// phyphox-test: corpus-valid-load
//Every file in phyphox-docs' corpus/valid and corpus/generated whose declared format version is
//at most PhyphoxFile.phyphoxFileVersion must load through the real loading path without error.
//Files declaring a newer version are skipped, not failed - they exist for future format
//versions. Contract: phyphox-docs/corpus/README.md, "The app test suites".
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
    public void loadsWithoutError() throws Exception {
        assumeTrue("No phyphox-docs checkout found next to this repository - corpus skipped.",
                !CorpusTestEnvironment.CORPUS_MISSING.equals(relativePath));

        File file = new File(CorpusTestEnvironment.findCorpus(), relativePath);
        int[] declared = CorpusTestEnvironment.declaredVersion(file);
        if (declared != null)
            assumeTrue("Declares format version " + declared[0] + "." + declared[1]
                            + " > supported " + PhyphoxFile.phyphoxFileVersion + " - skipped.",
                    CorpusTestEnvironment.versionAtMostSupported(declared));

        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(file, activity);
        assertTrue(relativePath + " failed to load: " + experiment.message, experiment.loaded);
    }
}
