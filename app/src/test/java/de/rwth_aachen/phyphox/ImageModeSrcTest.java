package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity;
import de.rwth_aachen.phyphox.ExperimentList.handler.ZipIntentHandler;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.ImageElement;

// phyphox-test: view-image-mode-src
//lightSrc and darkSrc of the image element (file format 1.21, rules.yml image-mode-src) on the shared
//container fixture mode-resources.zip (phyphox-docs fixtures/containers/README.md): plain.png grey,
//light.png red, dark.png blue, the last two referenced only through the alternatives.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class ImageModeSrcTest {

    private static final int GREY = Color.rgb(128, 128, 128);
    private static final int RED = Color.rgb(255, 0, 0);
    private static final int BLUE = Color.rgb(0, 0, 255);
    private static final int INVERTED_BLUE = Color.rgb(255, 255, 0);

    private PhyphoxExperiment experiment;

    @Before
    public void loadThroughTheIntakeRoute() throws Exception {
        File fixtures = CorpusTestEnvironment.findFixtures("containers");
        assumeTrue("no phyphox-docs checkout next to this repository", fixtures != null);
        File zip = new File(fixtures, "mode-resources.zip");
        assertThat(zip.isFile()).isTrue();

        Application application = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        ExperimentListActivity listActivity = Robolectric.buildActivity(ExperimentListActivity.class).get();
        if (listActivity.getBaseContext() == null)
            Shadows.shadowOf(listActivity).callAttach(new Intent());

        String result = new ZipIntentHandler(new Intent(Intent.ACTION_VIEW, Uri.fromFile(zip)), listActivity).execute().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertThat(result).isEmpty();
        Intent opened = Shadows.shadowOf(listActivity).getNextStartedActivity();
        assertThat(opened).isNotNull();

        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxFile.PhyphoxStream stream = PhyphoxFile.openXMLInputStream(opened, activity);
        assertThat(stream.errorMessage).isEmpty();
        experiment = PhyphoxFile.loadExperiment(stream, activity);
        assertThat(experiment.loaded).isTrue();
    }

    private List<ImageElement> images() {
        List<ImageElement> images = new ArrayList<>();
        for (ExpViewElement element : experiment.experimentViews.get(0).flatElements())
            if (element instanceof ImageElement)
                images.add((ImageElement) element);
        return images;
    }

    //The colour the element shows, filter included; null for the "Image not available" state
    private Integer shownColor(ImageElement image) {
        Context context = RuntimeEnvironment.getApplication();
        LinearLayout ll = new LinearLayout(context);
        image.createView(ll, context, context.getResources(), null, experiment);
        if (image.rootView instanceof TextView)
            return null;
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        image.drawable.setBounds(0, 0, 1, 1);
        image.drawable.draw(new Canvas(bitmap));
        return bitmap.getPixel(0, 0) | 0xff000000;
    }

    @Test
    public void everyFileNamedBySrcLightSrcOrDarkSrcIsAResource() {
        //What the save to the collection copies, a saved state writes into res/ and /res serves
        assertThat(experiment.resources).containsExactly("plain.png", "light.png", "dark.png");
        for (String name : experiment.resources)
            assertThat(new File(experiment.resourceFolder, name).isFile()).isTrue();
    }

    @Test
    public void lightModeShowsLightSrcOrSrc() {
        RuntimeEnvironment.setQualifiers("+notnight");
        List<ImageElement> images = images();
        assertThat(images).hasSize(3);
        assertThat(shownColor(images.get(0))).isEqualTo(RED);
        assertThat(shownColor(images.get(1))).isEqualTo(GREY);
        assertThat(shownColor(images.get(2))).isEqualTo(GREY);
    }

    @Test
    public void darkModeShowsDarkSrcWithTheDarkFilter() {
        RuntimeEnvironment.setQualifiers("+night");
        List<ImageElement> images = images();
        assertThat(shownColor(images.get(0))).isEqualTo(BLUE);
        assertThat(shownColor(images.get(1))).isEqualTo(BLUE);
        assertThat(shownColor(images.get(2))).isEqualTo(INVERTED_BLUE);
    }

    @Test
    public void theElementFollowsAModeChange() {
        ImageElement image = images().get(0);
        RuntimeEnvironment.setQualifiers("+night");
        assertThat(shownColor(image)).isEqualTo(BLUE);
        RuntimeEnvironment.setQualifiers("+notnight");
        assertThat(shownColor(image)).isEqualTo(RED);
    }

    @Test
    public void aMissingAlternativeDoesNotFallBackToSrc() {
        ImageElement image = new ImageElement(null, null, null, RuntimeEnvironment.getApplication().getResources(), "plain.png");
        image.setModeSources(null, "missing.png");
        RuntimeEnvironment.setQualifiers("+night");
        assertThat(shownColor(image)).isNull();
        RuntimeEnvironment.setQualifiers("+notnight");
        assertThat(shownColor(image)).isEqualTo(GREY);
    }

    @Test
    public void theWebInterfaceGetsTheFileOfEachMode() {
        List<ImageElement> images = images();
        assertThat(images.get(0).getViewHTML(0)).contains("src=\"res?src=plain.png\" data-light-src=\"res?src=light.png\" data-dark-src=\"res?src=dark.png\"");
        assertThat(images.get(1).getViewHTML(1)).contains("src=\"res?src=plain.png\" data-light-src=\"res?src=plain.png\" data-dark-src=\"res?src=dark.png\"");
        ImageElement plain = new ImageElement(null, null, null, RuntimeEnvironment.getApplication().getResources(), "plain.png");
        assertThat(plain.getViewHTML(2)).doesNotContain("data-");
    }
}
