package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

//Both slider types must be in the accessibility tree (iOS found its range slider missing, 2026-08-25).
//"uiautomator dump" drops them; the tree has to be walked as a service does.
@RunWith(AndroidJUnit4.class)
public class SliderAccessibilityTest {

    private static final String FIXTURE = "sliders-dropdowns.phyphox";

    //The accessibility tree as a service sees it, walked node by node from the active window.
    private List<String> accessibilityNodes() {
        List<String> nodes = new ArrayList<>();
        AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
        if (root != null)
            collect(root, nodes);
        return nodes;
    }

    private void collect(AccessibilityNodeInfo node, List<String> nodes) {
        nodes.add(node.getClassName() + " [" + node.getViewIdResourceName() + "] desc="
                + node.getContentDescription() + " text=" + node.getText());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null)
                collect(child, nodes);
        }
    }

    //The sliders' virtual-view nodes are populated on demand, so poll before concluding they are missing
    private List<String> awaitSliderNodes(long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        List<String> nodes = accessibilityNodes();
        while (System.currentTimeMillis() < deadline && sliderNodes(nodes) == 0) {
            Thread.sleep(250);
            nodes = accessibilityNodes();
        }
        return nodes;
    }

    private long sliderNodes(List<String> nodes) {
        long count = 0;
        for (String node : nodes)
            if (node.contains("Slider") || node.contains("SeekBar") || node.contains("sliderView"))
                count++;
        return count;
    }

    // phyphox-test: accessibility-smoke
    @Test
    public void bothSliderTypesAreInTheAccessibilityTree() throws Exception {
        assumeTrue("No phyphox-docs checkout was present at build time - fixtures skipped.",
                FixtureExperiment.available(FIXTURE));

        Experiment activity = FixtureExperiment.launch(FIXTURE);
        try {
            List<String> nodes = awaitSliderNodes(15000);
            long sliders = sliderNodes(nodes);

            assertTrue("The fixture holds a plain and a range slider, the accessibility tree has "
                    + sliders + ":\n  " + String.join("\n  ", nodes), sliders >= 2);

            //Both thumbs are addressable and announce the value the screen shows, not the step index
            String announced = String.join("\n  ", nodes);
            assertTrue("The range slider's lower thumb is not exposed:\n  " + announced,
                    announced.contains("Range start"));
            assertTrue("The range slider's upper thumb is not exposed:\n  " + announced,
                    announced.contains("Range end"));
            assertTrue("The announced values are step indices rather than what the screen shows "
                            + "(the fixture's range slider sits at 20 - 60):\n  " + announced,
                    announced.contains("Range start, 20") && announced.contains("Range end, 60"));
        } finally {
            FixtureExperiment.close(activity);
        }
    }

}
