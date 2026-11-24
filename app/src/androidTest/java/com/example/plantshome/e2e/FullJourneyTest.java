package com.example.plantshome.e2e;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.MediaStore;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import com.example.plantshome.R;
import com.example.plantshome.SplashActivity;
import com.example.plantshome.data.PlantPhotos;
import com.example.plantshome.reminder.ReminderScheduler;
import com.example.plantshome.reminder.ReminderWorker;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;

/**
 * One person's whole journey through the app, on a real Android system, against the
 * Firebase Auth emulator: sign up, verify, pick plants, use every screen, sign out,
 * sign back in, and delete the account. Saves a screenshot of each screen.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class FullJourneyTest {

    private static final long SHORT = 10_000;
    private static final long LONG = 30_000;
    private static final String NAME = "E2E Tester";
    private static final String PASSWORD = "PlantsHome#2026";

    /** On failure, keep a picture of what the phone showed. */
    @Rule
    public final TestWatcher screenshotOnFailure = new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description description) {
            E2E.screenshot("zz-failure");
        }
    };

    @Before
    public void setUp() {
        Intents.init();
    }

    @After
    public void tearDown() {
        Intents.release();
    }

    @Test
    public void wholeJourney() throws Exception {
        String email = E2E.uniqueEmail();
        Context app = E2E.app();

        // ---- Splash → Get Started → Login
        ActivityScenario.launch(SplashActivity.class);
        E2E.screenshot("01-splash");
        E2E.waitFor(withId(R.id.buttonGetStart), SHORT);
        E2E.screenshot("02-get-started");
        onView(withId(R.id.buttonGetStart)).perform(click());
        E2E.waitFor(withId(R.id.btnLogin), SHORT);

        // Empty login is rejected on the phone, before any network call.
        onView(withId(R.id.btnLogin)).perform(click());
        onView(withId(R.id.email)).check(matches(hasErrorText(app.getString(R.string.error_email_required))));
        E2E.screenshot("03-login-validation");

        // ---- Sign up
        onView(withId(R.id.register)).perform(click());
        E2E.waitFor(withId(R.id.registerUser), SHORT);
        onView(withId(R.id.fullName)).perform(typeText(NAME));
        onView(withId(R.id.email)).perform(typeText(email));
        onView(withId(R.id.password)).perform(typeText(PASSWORD));
        onView(withId(R.id.confirmPassword)).perform(typeText("not the same"));
        closeSoftKeyboard();
        onView(withId(R.id.registerUser)).perform(click());
        onView(withId(R.id.confirmPassword))
                .check(matches(hasErrorText(app.getString(R.string.error_passwords_do_not_match))));
        onView(withId(R.id.confirmPassword)).perform(replaceText(PASSWORD));
        closeSoftKeyboard();
        E2E.screenshot("04-signup");
        onView(withId(R.id.registerUser)).perform(click());

        // ---- Verify email: the app waits, the user opens the link, the app moves on by itself.
        E2E.waitFor(withId(R.id.verifyBtn), LONG);
        E2E.screenshot("05-verify-email");
        E2E.verifyEmailInEmulator(email);

        // ---- Choices
        E2E.waitFor(withId(R.id.btnRegister), LONG);
        onView(withId(R.id.button3)).perform(click()); // Boston Ivy on
        E2E.screenshot("06-choices");
        onView(withId(R.id.btnRegister)).perform(click());

        // ---- Home
        E2E.waitFor(withId(R.id.mainGridLayout), LONG);
        onView(withId(R.id.mainGridLayout)).check(E2E.childCount(10));
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        E2E.screenshot("07-home");

        // Search filters the plants and explains an empty result.
        onView(withId(R.id.search)).perform(click());
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(typeText("jade"));
        onView(withId(R.id.mainGridLayout)).check(E2E.childCount(3));
        closeSoftKeyboard();
        E2E.screenshot("08-search");
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("zzz"));
        onView(withId(R.id.text_home))
                .check(matches(withText(app.getString(R.string.search_no_results, "zzz"))));
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText(""));
        onView(withId(R.id.mainGridLayout)).check(E2E.childCount(10));
        closeSoftKeyboard();
        onView(withContentDescription("Collapse")).perform(click());

        // ---- Plant page + camera (the camera app is stubbed; the test supplies the photo)
        onView(withId(R.id.mainGridLayout)).perform(E2E.clickChild(0));
        E2E.waitFor(withId(R.id.plantName), SHORT);
        onView(withId(R.id.plantName)).check(matches(withText(R.string.plant_monstera_adansonii)));
        onView(withId(R.id.plantIntro)).check(matches(withText(containsString("Swiss Cheese"))));
        writeFakePhoto(PlantPhotos.pending(app, uid, 0));
        intending(hasAction(MediaStore.ACTION_IMAGE_CAPTURE))
                .respondWith(new Instrumentation.ActivityResult(Activity.RESULT_OK, null));
        onView(withId(R.id.camkbtn)).perform(click());
        waitUntil(() -> PlantPhotos.photo(app, uid, 0).exists(), SHORT, "photo saved");
        E2E.screenshot("09-plant-detail");
        onView(withId(R.id.backToMain)).perform(click());
        E2E.waitFor(withId(R.id.mainGridLayout), SHORT);

        // ---- Drawer → Daily Report → Badge
        openDrawer();
        E2E.screenshot("10-drawer");
        drawerItem(R.string.menu_report);
        E2E.waitFor(withId(R.id.reportbtn1), SHORT);
        onView(withId(R.id.reportbtn1)).perform(click());
        onView(withId(R.id.reportbtn1)).check(matches(withText(containsString("✓"))));
        E2E.screenshot("11-daily-report");
        onView(withId(R.id.allDone)).perform(click());
        E2E.waitFor(withId(R.id.badgeMessage), SHORT);
        onView(withId(R.id.badgeMessage)).check(matches(withText(R.string.badge_earned_first)));
        E2E.screenshot("12-badge");
        onView(withId(R.id.btnbadgestart)).perform(click());
        E2E.waitFor(withId(R.id.mainGridLayout), SHORT);

        // ---- Tasks: ticks are saved; Completed Task goes to Badges
        openDrawer();
        drawerItem(R.string.menu_taskpanel);
        E2E.waitFor(withId(R.id.taskCheck1), SHORT);
        onView(withId(R.id.taskCheck1)).perform(click());
        onView(withId(R.id.taskCheck1)).check(matches(isChecked()));
        E2E.screenshot("13-tasks");
        onView(withId(R.id.btnCompletedTask)).perform(click());
        E2E.waitFor(withId(R.id.badgeMessage), SHORT);

        // ---- Daily reminder (bell) and its notification
        onView(withId(R.id.notification)).perform(click());
        E2E.waitFor(withId(android.R.id.button1), SHORT);
        E2E.screenshot("14-reminder-dialog");
        onView(withId(android.R.id.button1)).perform(click());
        waitUntil(() -> ReminderScheduler.isEnabled(app), SHORT, "reminder enabled");
        ListenableWorker.Result result = TestWorkerBuilder
                .from(app, ReminderWorker.class, Executors.newSingleThreadExecutor())
                .build().doWork();
        assertEquals(ListenableWorker.Result.success(), result);
        assertTrue("Reminder notification shown", hasNotification(app));

        // ---- Profile shows the account
        openDrawer();
        drawerItem(R.string.menu_profile);
        E2E.waitFor(withId(R.id.profileName), SHORT);
        onView(withId(R.id.profileName)).check(matches(withText(app.getString(R.string.profile_name, NAME))));
        E2E.screenshot("15-profile");
        pressBack();
        E2E.waitFor(withId(R.id.drawer_layout), SHORT);

        // ---- Feedback: email filled in, empty feedback rejected
        openDrawer();
        drawerItem(R.string.menu_feedback);
        E2E.waitFor(withId(R.id.btnSubmitFeedback), SHORT);
        onView(withId(R.id.userName)).check(matches(withText(email)));
        onView(withId(R.id.btnSubmitFeedback)).perform(click());
        onView(withId(R.id.myFeedback))
                .check(matches(hasErrorText(app.getString(R.string.error_feedback_required))));
        E2E.screenshot("16-feedback");

        // ---- Sign out, sign back in: straight to Home (verified, choices remembered)
        openDrawer();
        drawerItem(R.string.menu_logout);
        E2E.waitFor(withId(R.id.btnLogin), SHORT);
        logIn(email);
        E2E.waitFor(withId(R.id.mainGridLayout), LONG);

        // ---- Delete the account; it can't log in afterwards
        openDrawer();
        drawerItem(R.string.menu_profile);
        E2E.waitFor(withId(R.id.deleteAccountRow), SHORT);
        onView(withId(R.id.deleteAccountRow)).perform(click());
        E2E.waitFor(withId(android.R.id.button1), SHORT);
        E2E.screenshot("17-delete-confirm");
        onView(withId(android.R.id.button1)).perform(click());
        E2E.waitFor(withId(R.id.btnLogin), LONG);
        assertTrue("Photos deleted with the account", !PlantPhotos.photo(app, uid, 0).exists());
        logIn(email);
        Thread.sleep(4000);
        onView(withId(R.id.btnLogin)).check(matches(allOf(isDisplayed(), isEnabled())));
        E2E.screenshot("18-deleted-cannot-login");
    }

    private static void logIn(String email) {
        onView(withId(R.id.email)).perform(replaceText(email));
        onView(withId(R.id.password)).perform(replaceText(PASSWORD));
        closeSoftKeyboard();
        onView(withId(R.id.btnLogin)).perform(click());
    }

    private static void openDrawer() {
        // A tap that lands while the screen is still settling after a transition can be lost;
        // try once more before failing.
        for (int attempt = 1; ; attempt++) {
            onView(withContentDescription("Open navigation drawer")).perform(click());
            try {
                E2E.waitFor(withId(R.id.nav_view), 5_000);
                return;
            } catch (AssertionError notOpen) {
                if (attempt == 2) {
                    throw notOpen;
                }
            }
        }
    }

    private static void drawerItem(int label) {
        onView(allOf(withText(label), isDescendantOfA(withId(R.id.nav_view)))).perform(click());
    }

    private static boolean hasNotification(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            if (context.getPackageName().equals(notification.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private static void writeFakePhoto(File file) throws Exception {
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        Bitmap bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(49, 79, 37));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
    }

    private interface Check {
        boolean ok() throws Exception;
    }

    private static void waitUntil(Check check, long timeoutMs, String what) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }
}
