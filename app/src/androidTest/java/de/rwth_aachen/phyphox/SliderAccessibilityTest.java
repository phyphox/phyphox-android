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

//A slider that is in no accessibility node cannot be found, announced or adjusted by TalkBack or
//Switch Control - the user simply cannot operate it. iOS found its range slider missing from the
//accessibility tree (2026-08-25); this asks the same question on Android, for both slider types.
//
//Note for whoever compares this with iOS: "uiautomator dump" does NOT show the sliders, because
//its hierarchy view drops them - the nodes are there when the tree is walked, which is what a
//service does. A dump alone would have concluded the opposite.
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

    //The accessibility tree of a freshly opened screen fills in as the window settles, and the
    //sliders expose themselves through a virtual-view helper that is populated on demand, so a
    //query right after the launch can legitimately come back without them. Poll until they show
    //up, and only conclude that they are missing when they stay missing.
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

            //Both thumbs of the range slider are addressable, and what a service announces is
            //the value the screen shows - not the internal step index the slider counts in.
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
