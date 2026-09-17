package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

//A saved state carries exactly one state-title, color and events element: writeStateFile replaces
//the metadata of the previous save. Files with two state-titles do not load on iOS at all.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class StateFileWriterTest {

    //In the shape the writer produces: the three metadata elements back-to-back with no whitespace.
    private static final String SAVED_STATE =
            "<phyphox version=\"1.20\">"
                    + "<title>Pendulum</title>"
                    + "<state-title>Measurement 1/23/18 21:13</state-title>"
                    + "<color>blue</color>"
                    + "<events><start experimentTime=\"0.0\" systemTime=\"1516738380000\"/></events>"
                    + "<category>Saved states</category>"
                    + "<description>A saved state for the state writer test.</description>"
                    + "<data-containers>"
                    + "<container size=\"0\" init=\"1,2,3\">t</container>"
                    + "</data-containers>"
                    + "<views><view label=\"Data\"><value label=\"t\"><input>t</input></value></view></views>"
                    + "</phyphox>";

    @Test
    public void replacesMetadataOfPreviousSave() throws Exception {
        //The simulated device can only be built once per test.
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();

        byte[] state = write(activity, SAVED_STATE.getBytes(StandardCharsets.UTF_8), "Pendulum on the swing");
        assertMetadata(state, "Pendulum on the swing");

        //The second save reads back its own output, whose metadata elements sit next to each other.
        byte[] resaved = write(activity, state, "Pendulum, second run");
        assertMetadata(resaved, "Pendulum, second run");
    }

    //Load a state file the way the app does and write it back out with a new title.
    private byte[] write(Experiment activity, byte[] source, String title) {
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(new ByteArrayInputStream(source), activity);
        assertTrue("Test state failed to load: " + experiment.message, experiment.loaded);
        //Set by openXMLInputStream when the app opens the file; this test opens the stream itself.
        experiment.source = source;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertNull(experiment.writeStateFile(title, out));
        return out.toByteArray();
    }

    private void assertMetadata(byte[] state, String expectedTitle) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(state));
        assertEquals("state-title elements", 1, rootChildren(doc, "state-title").size());
        assertEquals("color elements", 1, rootChildren(doc, "color").size());
        assertEquals("events elements", 1, rootChildren(doc, "events").size());
        assertEquals(expectedTitle, rootChildren(doc, "state-title").get(0).getTextContent());
    }

    private java.util.List<Node> rootChildren(Document doc, String name) {
        java.util.List<Node> result = new java.util.ArrayList<>();
        NodeList children = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
            if (children.item(i).getNodeName().equals(name))
                result.add(children.item(i));
        return result;
    }
}
