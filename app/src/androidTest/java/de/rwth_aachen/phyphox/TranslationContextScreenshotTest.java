package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import androidx.viewpager.widget.ViewPager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.ExperimentView.ExpView;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.GraphView;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.InteractiveGraphView;
import de.rwth_aachen.phyphox.ExperimentView.ValueElement;

//Not a test: captures the screenshots that give Weblate's translators context, driven by
//tools/translation_screenshots.py (-e translationScreenshots 1; every method skips itself without it), which also sets the app to English.
//Each shot leaves a PNG, the UiAutomator dump of all windows and the texts drawn on a canvas, which
//the window dump cannot see (graph labels and units, value units); the script resolves them to strings.
@RunWith(AndroidJUnit4.class)
public class TranslationContextScreenshotTest {

    private static final String PACKAGE = "de.rwth_aachen.phyphox";
    private static final int MAX_PAGES = 6;

    private String experimentFile; //the shipped experiment on screen, for the script's lookup

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    private Context app() {
        return getInstrumentation().getTargetContext();
    }

    private String string(int id) {
        return app().getString(id);
    }

    private static String argument(String name) {
        try {
            return InstrumentationRegistry.getArguments().getString(name);
        } catch (Exception e) {
            return null;
        }
    }

    @Before
    public void onlyFromTheScript() {
        assumeTrue("run by tools/translation_screenshots.py", "1".equals(argument("translationScreenshots")));
        FixtureExperiment.suppressHints();
    }

    @After
    public void closeEverything() {
        experimentFile = null;
        FixtureExperiment.close(FixtureExperiment.activity());
    }

    // ------------------------------------------------------------------ capture

    private File out() {
        return app().getExternalFilesDir("translation-screenshots");
    }

    private void shot(String name) throws Exception {
        Thread.sleep(800);
        File png = new File(out(), name + ".png");
        File parent = png.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new AssertionError("cannot create " + parent);
        if (!device().takeScreenshot(png))
            throw new AssertionError("no screenshot for " + name);
        try (OutputStream xml = new FileOutputStream(new File(out(), name + ".xml"))) {
            device().dumpWindowHierarchy(xml);
        }
        JSONObject meta = new JSONObject();
        meta.put("name", name);
        meta.put("experiment", experimentFile == null ? JSONObject.NULL : experimentFile);
        meta.put("canvas", canvasTexts());
        try (OutputStream json = new FileOutputStream(new File(out(), name + ".json"))) {
            json.write(meta.toString(1).getBytes(StandardCharsets.UTF_8));
        }
    }

    private Activity resumed() {
        final Activity[] holder = new Activity[1];
        getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                holder[0] = activity;
        });
        return holder[0];
    }

    private static boolean onScreen(View view) {
        return view.isShown() && view.getGlobalVisibleRect(new Rect());
    }

    private static Object field(Object object, String name) {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(object);
            } catch (NoSuchFieldException e) {
                //look in the superclass
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    private static void add(JSONArray list, Object text, String source) {
        if (text == null || text.toString().trim().isEmpty())
            return;
        try {
            list.put(new JSONObject().put("text", text.toString()).put("source", source));
        } catch (Exception ignored) {
        }
    }

    //What graphs and value elements draw themselves: axis labels, units and the empty-graph notes
    private JSONArray canvasTexts() {
        final JSONArray list = new JSONArray();
        final Activity activity = resumed();
        if (activity == null)
            return list;
        getInstrumentation().runOnMainSync(() -> {
            //Behind a dialog the experiment is only a dimmed backdrop
            if (activity.hasWindowFocus()) {
                collect(activity.getWindow().getDecorView(), activity.getResources(), list);
                if (activity instanceof Experiment)
                    valueUnits((Experiment) activity, list);
            }
            //Hints and read-outs are unfocusable popup windows, which the window dump leaves out
            Object hint = field(activity, "popupWindow");
            if (hint instanceof PopupWindow && ((PopupWindow) hint).isShowing())
                popupTexts(((PopupWindow) hint).getContentView(), list);
        });
        return list;
    }

    private void collect(View view, Resources res, JSONArray list) {
        if (!onScreen(view))
            return;
        if (view instanceof InteractiveGraphView) {
            TextView readOut = ((InteractiveGraphView) view).popupWindowText;
            if (readOut != null && readOut.isShown())
                popupTexts(readOut, list);
        }
        if (view instanceof GraphView) {
            for (String axis : new String[]{"X", "Y", "Z"}) {
                add(list, field(view, "label" + axis), "graph.label");
                add(list, field(view, "unit" + axis), "graph.unit");
            }
            Object status = field(view, "dataStatus");
            if (status != null) {
                switch (status.toString()) {
                    case "noData": add(list, res.getString(R.string.graph_no_data), "graph.status"); break;
                    case "noValidData": add(list, res.getString(R.string.graph_no_valid_data), "graph.status"); break;
                    case "noDataInRange": add(list, res.getString(R.string.graph_no_data_in_range), "graph.status"); break;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                collect(group.getChildAt(i), res, list);
        }
    }

    private static void popupTexts(View view, JSONArray list) {
        if (view instanceof TextView)
            for (String line : ((TextView) view).getText().toString().split("\n"))
                add(list, line.trim(), "popup");
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                popupTexts(group.getChildAt(i), list);
        }
    }

    //A value element shows number and unit in one text; the unit alone is what the strings know
    private static void valueUnits(Experiment activity, JSONArray list) {
        if (activity.experiment == null)
            return;
        for (ExpView expView : activity.experiment.experimentViews)
            for (ExpViewElement element : expView.flatElements())
                if (element instanceof ValueElement && element.rootView != null && onScreen(element.rootView)) {
                    Object unit = field(element, "unit");
                    Object displayUnitId = field(element, "displayUnitId");
                    if (displayUnitId != null)
                        add(list, Unit.reference(displayUnitId.toString()).symbol(activity.getResources()), "value.unit");
                    else if (unit instanceof Unit)
                        add(list, ((Unit) unit).symbol(activity.getResources()), "value.unit");
                }
    }

    //Shots of a scrolled screen until its end; scrolls the largest scrollable view in the activity
    private void shotPages(String name) throws Exception {
        shot(name + "-1");
        for (int page = 2; page <= MAX_PAGES; page++) {
            if (!scrollActivity(1))
                return;
            shot(name + "-" + page);
        }
    }

    private boolean scrollActivity(int direction) {
        final boolean[] moved = new boolean[1];
        final Activity activity = resumed();
        if (activity == null)
            return false;
        getInstrumentation().runOnMainSync(() -> {
            View scrollable = largestScrollable(activity.getWindow().getDecorView(), null);
            if (scrollable == null || !scrollable.canScrollVertically(direction))
                return;
            if (direction > 0)
                scrollable.scrollBy(0, Math.round(scrollable.getHeight() * 0.85f));
            else
                scrollable.scrollTo(0, 0);
            moved[0] = true;
        });
        return moved[0];
    }

    private static View largestScrollable(View view, View best) {
        if (!onScreen(view))
            return best;
        if ((view.canScrollVertically(1) || view.canScrollVertically(-1))
                && (best == null || view.getHeight() * view.getWidth() > best.getHeight() * best.getWidth()))
            best = view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                best = largestScrollable(group.getChildAt(i), best);
        }
        return best;
    }

    //Dialogs live in their own window: those are scrolled through UiAutomator
    private void shotDialogPages(String name) throws Exception {
        shot(name + "-1");
        for (int page = 2; page <= MAX_PAGES; page++) {
            UiObject2 scrollable = device().findObject(By.scrollable(true).pkg(PACKAGE));
            if (scrollable == null || !scrollable.scroll(Direction.DOWN, 0.8f))
                return;
            shot(name + "-" + page);
        }
    }

    // --------------------------------------------------------------- navigation

    private static BySelector text(String text) {
        return By.text(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE));
    }

    private UiObject2 find(BySelector selector) {
        return device().wait(Until.findObject(selector), 5000);
    }

    private boolean click(BySelector selector) throws InterruptedException {
        UiObject2 object = find(selector);
        if (object == null)
            return false;
        object.click();
        Thread.sleep(700);
        return true;
    }

    private boolean clickText(int id) throws InterruptedException {
        return click(text(string(id)));
    }

    //By class first: the dialog's title often carries the same text as its button (see SavedStateCollectionTest)
    private boolean clickButton(int id) throws InterruptedException {
        BySelector button = By.clazz("android.widget.Button").text(Pattern.compile(Pattern.quote(string(id)), Pattern.CASE_INSENSITIVE));
        return click(button) || clickText(id);
    }

    private boolean clickDesc(int id) throws InterruptedException {
        return click(By.desc(string(id)));
    }

    private boolean openOverflow() throws InterruptedException {
        return click(By.desc("More options"));
    }

    private void back() throws InterruptedException {
        device().pressBack();
        Thread.sleep(700);
    }

    //Back only while a dialog, sheet or menu holds the focus: on the bare experiment it would leave it
    private void dismiss() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            Activity activity = resumed();
            final boolean[] focused = new boolean[1];
            if (activity != null)
                getInstrumentation().runOnMainSync(() -> focused[0] = activity.hasWindowFocus());
            if (activity == null || focused[0])
                return;
            back();
        }
    }

    //A toolbar icon where there is room for it, an overflow entry otherwise
    private boolean menuAction(int id) throws InterruptedException {
        UiObject2 icon = device().findObject(By.desc(string(id)));
        if (icon != null) {
            icon.click();
            Thread.sleep(700);
            return true;
        }
        if (!openOverflow())
            return false;
        if (clickText(id))
            return true;
        dismiss();
        return false;
    }

    private void openCollection() throws InterruptedException {
        Intent intent = app().getPackageManager().getLaunchIntentForPackage(app().getPackageName());
        if (intent == null)
            throw new AssertionError("no launcher intent");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        app().startActivity(intent);
        device().wait(Until.hasObject(By.res(PACKAGE + ":id/newExperiment")), 15000);
        Thread.sleep(1000);
    }

    private void hints(boolean info, boolean start) {
        app().getSharedPreferences(Const.PREFS_NAME, 0).edit()
                .putInt("menuHintDismissCount", info ? 0 : 3)
                .putInt("startHintDismissCount", start ? 0 : 3)
                .commit();
    }

    private Experiment launch(String asset) {
        experimentFile = asset;
        return FixtureExperiment.launchAsset(asset);
    }

    //FixtureExperiment suppresses the one-off hints, which are wanted here once
    private void launchWithHints(String asset, boolean info, boolean start) {
        experimentFile = asset;
        FixtureExperiment.suppressHints();
        hints(info, start);
        Intent intent = new Intent(app(), Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(Const.EXPERIMENT_XML, asset);
        intent.putExtra(Const.EXPERIMENT_ISASSET, true);
        app().startActivity(intent);
        FixtureExperiment.awaitLoaded();
    }

    private boolean dialogOpen() {
        return device().hasObject(By.res("android:id/button1"));
    }

    // --------------------------------------------------------------- the screens

    @Test
    public void collection() throws Exception {
        app().getSharedPreferences(Const.PREFS_NAME, 0).edit().putBoolean("skipWarning", false).commit();
        openCollection();
        shot("app/first-start-warning");
        clickButton(R.string.ok);
        FixtureExperiment.suppressHints();

        shotPages("app/collection");
        scrollActivity(-1);

        clickDesc(R.string.info);
        shot("app/help-menu");
        clickText(R.string.credits);
        shotDialogPages("app/credits");
        back();

        clickDesc(R.string.info);
        clickText(R.string.deviceInfo);
        shotDialogPages("app/device-info");
        back();

        click(By.res(PACKAGE + ":id/newExperiment"));
        shot("app/add-experiment-menu");
        click(By.res(PACKAGE + ":id/newExperimentSimple"));
        shotDialogPages("app/add-simple-experiment");
        back();
        back();
    }

    //The entries the user adds: a simple experiment and a saved state, with their menus and dialogs
    @Test
    public void collectionItems() throws Exception {
        launch("accelerometer.phyphox");
        menuAction(R.string.start);
        Thread.sleep(2000);
        menuAction(R.string.stop);
        if (menuAction(R.string.save_state)) {
            click(By.res(PACKAGE + ":id/imageSave"));
            Thread.sleep(1500);
        }
        FixtureExperiment.close(FixtureExperiment.activity());
        experimentFile = null;

        openCollection();
        UiObject2 list = find(By.res(PACKAGE + ":id/experimentScroller"));
        if (list != null && list.scrollUntil(Direction.DOWN, Until.findObject(text("Depth Sensor (LiDAR / ToF)"))) != null
                && click(text("Depth Sensor (LiDAR / ToF)"))) {
            shot("app/sensor-not-available");
            dismiss();
        }

        click(By.res(PACKAGE + ":id/newExperiment"));
        click(By.res(PACKAGE + ":id/newExperimentSimple"));
        UiObject2 title = find(By.res(PACKAGE + ":id/neTitle"));
        if (title != null) {
            title.setText("My experiment");
            click(By.res(PACKAGE + ":id/neAccelerometer"));
            clickButton(R.string.ok);
            FixtureExperiment.awaitLoaded();
            experimentFile = null;
            shot("app/simple-experiment");
            FixtureExperiment.close(FixtureExperiment.activity());
        }

        openCollection();
        //The state is named by the file name template: the experiment's title, date and time
        for (String entry : new String[]{"Acceleration with g .+", "My experiment"}) {
            BySelector entryTitle = By.res(PACKAGE + ":id/expTitle").text(Pattern.compile(entry));
            scrollActivity(-1);
            Thread.sleep(500);
            UiObject2 item = device().findObject(entryTitle);
            list = find(By.res(PACKAGE + ":id/experimentScroller"));
            if (item == null && list != null)
                item = list.scrollUntil(Direction.DOWN, Until.findObject(entryTitle));
            if (item == null)
                continue;
            String name = entry.equals("My experiment") ? "app/simple-experiment-entry" : "app/saved-state-entry";
            shot(name);
            UiObject2 menu = item.getParent().findObject(By.res(PACKAGE + ":id/menuButton"));
            if (menu == null)
                continue;
            menu.click();
            Thread.sleep(700);
            shot(name + "-menu");
            if (clickText(R.string.rename)) {
                shot("app/rename");
                clickButton(R.string.cancel);
                menu.click();
                Thread.sleep(700);
            }
            if (clickText(R.string.delete)) {
                shot(name.equals("app/saved-state-entry") ? "app/delete-saved-state" : "app/delete-experiment");
                clickButton(R.string.delete);
                Thread.sleep(1000);
            }
            dismiss();
        }
    }

    @Test
    public void settings() throws Exception {
        openCollection();
        clickDesc(R.string.info);
        clickText(R.string.settings);
        shotPages("app/settings");
        scrollActivity(-1);
        for (int[] entry : new int[][]{
                {R.string.settingsLanguage, 0}, {R.string.settings_theme_title, 1},
                {R.string.settingsUnitSystem, 2}, {R.string.settingsFileNameFormat, 3}}) {
            String[] names = {"language", "dark-mode", "unit-system", "file-names"};
            UiObject2 item = device().findObject(By.scrollable(true).pkg(PACKAGE));
            if (item != null)
                item.scrollUntil(Direction.DOWN, Until.findObject(text(string(entry[0]))));
            if (!clickText(entry[0]))
                continue;
            shotDialogPages("app/settings-" + names[entry[1]]);
            back();
        }
        back();
    }

    @Test
    public void experimentChrome() throws Exception {
        //The two one-off hints
        launchWithHints("pendulum.phyphox", true, false);
        Thread.sleep(1500);
        shot("app/hint-experiment-info");
        FixtureExperiment.close(FixtureExperiment.activity());
        //The start hint follows on a later launch; a raw sensor experiment never shows the info hint
        launchWithHints("accelerometer.phyphox", false, true);
        Thread.sleep(1500);
        shot("app/hint-start");
        hints(false, false);
        FixtureExperiment.close(FixtureExperiment.activity());

        Experiment activity = launch("accelerometer.phyphox");
        shot("app/experiment-toolbar");
        if (openOverflow()) {
            shot("app/experiment-menu");
            dismiss();
        }

        if (menuAction(R.string.show_description)) {
            shotDialogPages("app/experiment-info");
            dismiss();
        }

        if (menuAction(R.string.timedRun)) {
            shot("app/timed-run-off");
            clickText(R.string.enableTimedRun);
            shotDialogPages("app/timed-run-on");
            dismiss();
        }

        if (menuAction(R.string.export)) {
            shot("app/export");
            dismiss();
        }

        if (menuAction(R.string.save_state)) {
            shot("app/save-state");
            dismiss();
        }

        //Long enough for the leave confirmation (more than 10 s analysed)
        if (menuAction(R.string.start)) {
            Thread.sleep(11000);
            shot("app/experiment-running");
            menuAction(R.string.stop);
        }

        if (menuAction(R.string.clear_data)) {
            shot("app/clear-data");
            clickButton(R.string.cancel);
            dismiss();
        }

        if (menuAction(R.string.remoteServer)) {
            shot("app/remote-access-warning");
            if (clickButton(R.string.ok)) {
                Thread.sleep(1500);
                shot("app/remote-access-active");
                menuAction(R.string.remoteServer);
            }
            dismiss();
        }

        back();
        shot("app/leave-experiment");
        clickButton(R.string.cancel);
        dismiss();
        activity = FixtureExperiment.activity();
        if (activity == null)
            return;

        graphTools(activity);
        unitPicker(activity);
    }

    private InteractiveGraphView firstGraph(Experiment activity) {
        final InteractiveGraphView[] graph = new InteractiveGraphView[1];
        getInstrumentation().runOnMainSync(() -> {
            for (ExpView view : activity.experiment.experimentViews)
                for (ExpViewElement element : view.flatElements())
                    if (graph[0] == null && element.rootView instanceof InteractiveGraphView && onScreen(element.rootView))
                        graph[0] = (InteractiveGraphView) element.rootView;
        });
        return graph[0];
    }

    private int[] center(View view, float fx, float fy) {
        final int[] point = new int[2];
        getInstrumentation().runOnMainSync(() -> {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            point[0] = location[0] + Math.round(view.getWidth() * fx);
            point[1] = location[1] + Math.round(view.getHeight() * fy);
        });
        return point;
    }

    private void tap(View view, float fx, float fy) throws InterruptedException {
        int[] point = center(view, fx, fy);
        device().click(point[0], point[1]);
        Thread.sleep(1000);
    }

    private void graphTools(Experiment activity) throws Exception {
        InteractiveGraphView graph = firstGraph(activity);
        if (graph == null)
            return;
        tap(graph, 0.5f, 0.5f);
        shot("app/graph-maximized");

        View pick = graph.findViewById(R.id.graph_tools_pick);
        if (pick != null) {
            tap(pick, 0.5f, 0.5f);
            tap(graph.graphView, 0.6f, 0.5f);
            shot("app/graph-pick");
            //A second point shows the difference and the slope
            int[] from = center(graph.graphView, 0.3f, 0.5f), to = center(graph.graphView, 0.8f, 0.5f);
            device().swipe(from[0], from[1], to[0], to[1], 40);
            Thread.sleep(1000);
            shot("app/graph-pick-two-points");
        }

        View more = graph.findViewById(R.id.graph_tools_more);
        if (more != null) {
            tap(more, 0.5f, 0.5f);
            shot("app/graph-more-tools");
            if (clickText(R.string.graph_tools_linear_fit))
                shot("app/graph-linear-fit");
            dismiss();
        }

        //Panning leaves a zoomed state behind, which "Keep this view?" asks about on the way out
        View pan = graph.findViewById(R.id.graph_tools_pan);
        if (pan != null) {
            tap(pan, 0.5f, 0.5f);
            int[] from = center(graph.graphView, 0.7f, 0.5f), to = center(graph.graphView, 0.4f, 0.6f);
            device().swipe(from[0], from[1], to[0], to[1], 40);
            Thread.sleep(800);
        }
        back();
        shot("app/keep-this-view");
        if (clickButton(R.string.applyZoomMoreOptions))
            shotDialogPages("app/keep-this-view-options");
        dismiss();
        Thread.sleep(800);
    }

    private void unitPicker(Experiment activity) throws Exception {
        //The value elements live on a later tab; a tap on a convertible value opens the unit picker
        ViewPager pager = activity.findViewById(R.id.view_pager);
        int tabs = activity.experiment.experimentViews.size();
        for (int tab = 0; tab < tabs; tab++) {
            final int t = tab;
            getInstrumentation().runOnMainSync(() -> pager.setCurrentItem(t, false));
            Thread.sleep(800);
            final TextView[] value = new TextView[1];
            getInstrumentation().runOnMainSync(() -> {
                for (ExpViewElement element : activity.experiment.experimentViews.get(t).flatElements())
                    if (value[0] == null && element instanceof ValueElement && element.rootView != null)
                        value[0] = clickableText(element.rootView);
            });
            if (value[0] != null && onScreen(value[0])) {
                tap(value[0], 0.5f, 0.5f);
                shotDialogPages("app/unit-picker");
                dismiss();
                return;
            }
        }
    }

    private static TextView clickableText(View view) {
        if (view instanceof TextView && view.hasOnClickListeners())
            return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = clickableText(group.getChildAt(i));
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    //Every shipped experiment, each tab scrolled to its end, after a short measurement where there is
    //a sensor to give one. The script gets the list (-e experiments a,b,…) or takes all of them.
    @Test
    public void experiments() throws Exception {
        String[] files;
        String list = argument("experiments");
        if (list != null && !list.trim().isEmpty())
            files = list.split(",");
        else
            files = app().getAssets().list("experiments");
        if (files == null)
            return;
        Arrays.sort(files);
        for (String file : files) {
            if (!file.endsWith(".phyphox"))
                continue;
            captureExperiment(file.trim());
        }
    }

    private void captureExperiment(String file) throws Exception {
        String name = "experiments/" + file.substring(0, file.length() - ".phyphox".length());
        experimentFile = file;
        FixtureExperiment.launchAssetWithoutWaiting(file);
        Experiment activity = null;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            activity = FixtureExperiment.activity();
            if (activity != null && activity.experiment != null && activity.experiment.loaded)
                break;
            activity = null;
            Thread.sleep(200);
        }
        Thread.sleep(1000);
        if (activity == null) {
            //An experiment that only opens a web page leaves the app: nothing to show
            if (!device().hasObject(By.pkg(PACKAGE))) {
                device().pressHome();
                experimentFile = null;
                return;
            }
            //Not loadable here (a missing sensor, for example): the error is context as well
            shot(name + "/error");
            back();
            FixtureExperiment.close(FixtureExperiment.activity());
            experimentFile = null;
            return;
        }

        //A first-run dialog of the experiment itself (photosensitivity, network privacy, …)
        if (dialogOpen()) {
            shot(name + "/dialog");
            clickButton(R.string.ok);
        }

        if (clickDesc(R.string.start)) {
            Thread.sleep(3000);
            if (dialogOpen()) {
                shot(name + "/start-dialog");
                clickButton(R.string.ok);
                Thread.sleep(2000);
            }
            clickDesc(R.string.stop);
            dismiss();
        }

        ViewPager pager = activity.findViewById(R.id.view_pager);
        int tabs = activity.experiment.experimentViews.size();
        for (int tab = 0; tab < tabs; tab++) {
            final int t = tab;
            getInstrumentation().runOnMainSync(() -> pager.setCurrentItem(t, false));
            Thread.sleep(800);
            shotPages(name + "/tab" + (tab + 1));
        }

        if (menuAction(R.string.show_description)) {
            shotDialogPages(name + "/info");
            dismiss();
        }
        FixtureExperiment.close(activity);
        Thread.sleep(500);
        experimentFile = null;
    }
}
