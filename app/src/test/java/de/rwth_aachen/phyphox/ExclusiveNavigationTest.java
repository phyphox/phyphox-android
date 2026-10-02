package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.ExperimentView.GraphElement;

//The ways out of a maximized element: system back and the toolbar's arrow first close the exclusive view and leave the
//experiment only without one; a tab change while an element is maximized is held back until the exclusive view is gone
//(a zoomed graph asks first, Cancel stays).
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class ExclusiveNavigationTest {

    private static final String XML = "<phyphox xmlns=\"http://phyphox.org/xml\" version=\"1.21\" locale=\"en\">"
            + "<title>t</title><category>c</category><description>d</description>"
            + "<data-containers><container size=\"0\">x</container><container size=\"0\">y</container></data-containers>"
            + "<input></input><analysis></analysis><views>"
            + "<view label=\"one\"><graph label=\"g1\" labelX=\"t\" unitX=\"s\" labelY=\"a\" unitY=\"m/s²\"><input axis=\"x\">x</input><input axis=\"y\">y</input></graph></view>"
            + "<view label=\"two\"><graph label=\"g2\" labelX=\"t\" unitX=\"s\" labelY=\"a\" unitY=\"m/s²\"><input axis=\"x\">x</input><input axis=\"y\">y</input></graph></view>"
            + "</views></phyphox>";

    private static ActivityController<Experiment> launch() throws Exception {
        File target = new File(RuntimeEnvironment.getApplication().getFilesDir(), "exclusive-test.phyphox");
        Files.write(target.toPath(), XML.getBytes(StandardCharsets.UTF_8));
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Const.EXPERIMENT_XML, "exclusive-test.phyphox");
        intent.putExtra(Const.EXPERIMENT_ISASSET, false);
        ActivityController<Experiment> controller = Robolectric.buildActivity(Experiment.class, intent).setup();
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            PhyphoxExperiment experiment = controller.get().experiment;
            if (experiment != null && experiment.loaded && experiment.experimentViews.get(0).elements.get(0).rootView != null && controller.get().getCurrentExpViewFragment() != null)
                return controller;
            Thread.sleep(20);
        }
        throw new AssertionError("did not load: " + (controller.get().experiment == null ? "no experiment" : controller.get().experiment.message));
    }

    private static GraphElement maximizeFirstGraph(Experiment activity) {
        GraphElement graph = (GraphElement) activity.experiment.experimentViews.get(0).elements.get(0);
        activity.getCurrentExpViewFragment().requestExclusive(graph);
        ShadowLooper.idleMainLooper();
        assertThat(activity.getCurrentExpViewFragment().hasExclusive()).isTrue();
        return graph;
    }

    @Test
    public void theToolbarArrowClosesTheExclusiveViewFirstAndLeavesOnlyAfterwards() throws Exception {
        ActivityController<Experiment> controller = launch();
        try {
            Experiment activity = controller.get();
            maximizeFirstGraph(activity);
            activity.onOptionsItemSelected(new RoboMenuItem(android.R.id.home));
            ShadowLooper.idleMainLooper();
            assertThat(activity.getCurrentExpViewFragment().hasExclusive()).isFalse();
            assertThat(activity.isFinishing()).isFalse();
            activity.onOptionsItemSelected(new RoboMenuItem(android.R.id.home));
            ShadowLooper.idleMainLooper();
            assertThat(activity.isFinishing()).isTrue(); //a short run leaves without the confirmation
        } finally {
            controller.close();
        }
    }

    @Test
    public void systemBackClosesTheExclusiveViewFirstAndLeavesOnlyAfterwards() throws Exception {
        ActivityController<Experiment> controller = launch();
        try {
            Experiment activity = controller.get();
            maximizeFirstGraph(activity);
            activity.getOnBackPressedDispatcher().onBackPressed();
            ShadowLooper.idleMainLooper();
            assertThat(activity.getCurrentExpViewFragment().hasExclusive()).isFalse();
            assertThat(activity.isFinishing()).isFalse();
            activity.getOnBackPressedDispatcher().onBackPressed();
            ShadowLooper.idleMainLooper();
            assertThat(activity.isFinishing()).isTrue();
        } finally {
            controller.close();
        }
    }

    @Test
    public void theZoomAndFollowStateSurviveARecreatedView() throws Exception {
        ActivityController<Experiment> controller = launch();
        try {
            Experiment activity = controller.get();
            GraphElement graph = (GraphElement) activity.experiment.experimentViews.get(0).elements.get(0);
            graph.applyZoom(2, 4, true, null, null, false, false); //keep and follow new data
            graph.getZoomState().previouslyKept = true;
            activity.getCurrentExpViewFragment().recreateView();
            ShadowLooper.idleMainLooper();
            assertThat(graph.getZoomState().follows).isTrue();
            assertThat(graph.getZoomState().maxX - graph.getZoomState().minX).isWithin(1e-9).of(2.0);
            assertThat(graph.getZoomState().previouslyKept).isTrue();
        } finally {
            controller.close();
        }
    }

    @Test
    public void aTabChangeWaitsForTheExclusiveViewAndAZoomedGraphAsksFirst() throws Exception {
        ActivityController<Experiment> controller = launch();
        try {
            Experiment activity = controller.get();
            assertThat(activity.pager.getCurrentItem()).isEqualTo(0);
            //not zoomed: the tab change closes the view and moves on at once
            maximizeFirstGraph(activity);
            activity.tabLayout.getTabAt(1).select();
            ShadowLooper.idleMainLooper();
            assertThat(activity.pager.getCurrentItem()).isEqualTo(1);
            activity.tabLayout.getTabAt(0).select();
            ShadowLooper.idleMainLooper();
            assertThat(activity.pager.getCurrentItem()).isEqualTo(0);
            assertThat(activity.getCurrentExpViewFragment().hasExclusive()).isFalse();

            //zoomed: the question comes up, the pager stays, Cancel keeps the maximized graph
            GraphElement graph = maximizeFirstGraph(activity);
            graph.applyZoom(2, 4, false, null, null, false, false);
            activity.tabLayout.getTabAt(1).select();
            ShadowLooper.idleMainLooper();
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertThat(dialog).isNotNull();
            assertThat(dialog.isShowing()).isTrue();
            assertThat(activity.pager.getCurrentItem()).isEqualTo(0);
            assertThat(activity.tabLayout.getSelectedTabPosition()).isEqualTo(0);
            dialog.cancel();
            ShadowLooper.idleMainLooper();
            assertThat(activity.getCurrentExpViewFragment().hasExclusive()).isTrue();
            assertThat(activity.pager.getCurrentItem()).isEqualTo(0);

            //answered: the view closes and the pager moves to the tab that was tapped
            activity.tabLayout.getTabAt(1).select();
            ShadowLooper.idleMainLooper();
            AlertDialog question = (AlertDialog) ShadowDialog.getLatestDialog();
            assertThat(question.isShowing()).isTrue();
            question.getButton(DialogInterface.BUTTON_POSITIVE).performClick(); //"Keep this section"
            ShadowLooper.idleMainLooper();
            assertThat(activity.getCurrentExpViewFragment().hasExclusive()).isFalse();
            assertThat(activity.pager.getCurrentItem()).isEqualTo(1);
        } finally {
            controller.close();
        }
    }
}
