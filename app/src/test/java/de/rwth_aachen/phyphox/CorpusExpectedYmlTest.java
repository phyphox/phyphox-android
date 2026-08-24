package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

//The reader for the platform expectations in an expected.yml next to valid/ or generated/. The corpus
//currently carries a single entry, all of them "accepts" for Android, so the "rejects" branch
//- which asserts a deliberate platform difference by requiring the file to be refused - would
//otherwise never be exercised until a future corpus entry needs it.
//Contract: phyphox-docs/corpus/README.md, "The app test suites" -> platform differences.
public class CorpusExpectedYmlTest {

    private static final String EXPECTED_YML =
            "# A comment mentioning some-file.phyphox: which must not be read as an entry\n"
                    + "\n"
                    + "android-only.phyphox:\n"
                    + "  parser:\n"
                    + "    android: accepts\n"
                    + "    ios: rejects     # a trailing comment\n"
                    + "\n"
                    + "ios-only.phyphox:\n"
                    + "  parser:\n"
                    + "    android: rejects\n"
                    + "    ios: accepts\n";

    @Test
    public void readsPerPlatformExpectations() throws Exception {
        File corpus = Files.createTempDirectory("corpus").toFile();
        File generated = new File(corpus, "generated");
        File nested = new File(generated, "nested");
        if (!nested.mkdirs())
            throw new IllegalStateException("Cannot create temporary corpus");
        Files.write(new File(generated, "expected.yml").toPath(), EXPECTED_YML.getBytes(StandardCharsets.UTF_8));

        assertEquals("accepts", CorpusTestEnvironment.androidExpectation(corpus, "generated/android-only.phyphox"));
        assertEquals("rejects", CorpusTestEnvironment.androidExpectation(corpus, "generated/ios-only.phyphox"));
        //A file in a subdirectory is covered by the expected.yml above it.
        assertEquals("rejects", CorpusTestEnvironment.androidExpectation(corpus, "generated/nested/ios-only.phyphox"));
        //No entry is the normal case: the file just has to load like every other valid file.
        assertNull(CorpusTestEnvironment.androidExpectation(corpus, "generated/plain.phyphox"));
        //A directory without an expected.yml at all.
        assertNull(CorpusTestEnvironment.androidExpectation(corpus, "valid/plain.phyphox"));
    }
}
