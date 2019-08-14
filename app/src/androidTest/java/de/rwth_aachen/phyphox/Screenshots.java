package de.rwth_aachen.phyphox;

import android.content.Intent;
import android.net.Uri;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeLeft;


@RunWith(AndroidJUnit4.class)
public class Screenshots {
    @ClassRule
    public static final TestRule classRule = new LocaleTestRule();

    @Rule
    public final ActivityTestRule<ExperimentList> activityTestRule = new ActivityTestRule<>(ExperimentList.class, true, false);

    @Rule
    public final ActivityTestRule<Experiment> experimentActivityTestRule = new ActivityTestRule<>(Experiment.class, true, false);

    @Test
    public void takeScreenshotsOfMainScreen() throws Exception {
        Screengrab.setDefaultScreenshotStrategy(new UiAutomatorScreenshotStrategy());

        activityTestRule.launchActivity(null);

        try {
            Espresso.onView(ViewMatchers.withText(R.string.ok)).perform(click());
        } catch (Exception e) {

        }

        Screengrab.screenshot("main");
    }

    private <T> Matcher<T> first(final Matcher<T> matcher) {
        return new BaseMatcher<T>() {
            boolean isFirst = true;

            @Override
            public boolean matches(final Object item) {
                if (isFirst && matcher.matches(item)) {
                    isFirst = false;
                    return true;
                }

                return false;
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("should return first matching item");
            }
        };
    }

    @Test
    public void takeScreenshotsOfExperiment() throws Exception {
        Screengrab.setDefaultScreenshotStrategy(new UiAutomatorScreenshotStrategy());

        Intent i = new Intent();
        i.setData(Uri.parse("phyphox://rwth-aachen.sciebo.de/s/mezViL5TH4gyEe5/download"));
        i.setAction(Intent.ACTION_VIEW);
        experimentActivityTestRule.launchActivity(i);

        Thread.sleep(1000);

        Espresso.onView(ViewMatchers.withId(android.R.id.button2)).perform(click());

        Screengrab.screenshot("screen1");

        Espresso.onView(first(ViewMatchers.withId(R.id.graph_expand_image))).perform(click());

        Screengrab.screenshot("screen2");

        Espresso.onView(first(ViewMatchers.withId(R.id.graph_collapse_image))).perform(click());
        Espresso.onView(ViewMatchers.withId(R.id.view_pager)).perform(swipeLeft());
        Espresso.onView(ViewMatchers.withId(R.id.view_pager)).perform(swipeLeft());

        Thread.sleep(1000);

        Screengrab.screenshot("screen3");
    }
}
