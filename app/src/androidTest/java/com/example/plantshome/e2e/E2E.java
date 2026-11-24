package com.example.plantshome.e2e;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;

import android.app.Activity;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewAssertion;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.hamcrest.Matcher;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;

/** Small helpers for the end-to-end test. */
final class E2E {

    private static final String TAG = "PlantsHomeE2E";

    private E2E() {
    }

    static Context app() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /** Waits until a view matching {@code matcher} is on screen (async Firebase calls, splash delay). */
    static void waitFor(Matcher<View> matcher, long timeoutMs) {
        long end = SystemClock.uptimeMillis() + timeoutMs;
        Throwable last = null;
        while (SystemClock.uptimeMillis() < end) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (Throwable notYet) {
                last = notYet;
                SystemClock.sleep(250);
            }
        }
        throw new AssertionError("Timed out after " + timeoutMs + " ms waiting for " + matcher, last);
    }

    /** Saves a screenshot of the whole phone screen (status bar included) as files/e2e/NAME.png. */
    static void screenshot(String name) {
        SystemClock.sleep(700); // let ripples, toasts and fades settle
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Bitmap bitmap = automation.takeScreenshot();
        if (bitmap == null) {
            Log.w(TAG, "Screenshot failed: " + name);
            return;
        }
        File folder = new File(app().getFilesDir(), "e2e");
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            Log.w(TAG, "Could not save screenshot " + name, e);
        }
        Log.i(TAG, "Screenshot " + name);
    }

    /** Clicks the child at {@code index} of a ViewGroup (e.g. a card in the home grid). */
    static ViewAction clickChild(int index) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return org.hamcrest.Matchers.instanceOf(ViewGroup.class);
            }

            @Override
            public String getDescription() {
                return "click child " + index;
            }

            @Override
            public void perform(UiController uiController, View view) {
                ((ViewGroup) view).getChildAt(index).performClick();
                uiController.loopMainThreadUntilIdle();
            }
        };
    }

    static ViewAssertion childCount(int expected) {
        return (view, noView) -> {
            if (noView != null) {
                throw noView;
            }
            int actual = ((ViewGroup) view).getChildCount();
            if (actual != expected) {
                throw new AssertionError("Expected " + expected + " children but found " + actual);
            }
        };
    }

    static Activity currentActivity() {
        Activity[] found = new Activity[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            found[0] = resumed.isEmpty() ? null : resumed.iterator().next();
        });
        return found[0];
    }

    /**
     * Opens the email-verification link the Auth emulator "sent" to {@code email},
     * which is what a user does from their inbox.
     */
    static void verifyEmailInEmulator(String email) throws Exception {
        String host = E2ERunner.authEmulatorHost();
        if (host == null) {
            throw new IllegalStateException("Run with -e authEmulatorHost <host>");
        }
        String base = "http://" + host + ":" + E2ERunner.AUTH_EMULATOR_PORT;
        String projectId = com.google.firebase.FirebaseApp.getInstance().getOptions().getProjectId();
        long end = SystemClock.uptimeMillis() + 20_000;
        while (SystemClock.uptimeMillis() < end) {
            JSONArray codes = new JSONObject(get(base + "/emulator/v1/projects/" + projectId + "/oobCodes"))
                    .optJSONArray("oobCodes");
            for (int i = codes == null ? 0 : codes.length() - 1; i >= 0; i--) {
                JSONObject code = codes.getJSONObject(i);
                if ("VERIFY_EMAIL".equals(code.optString("requestType"))
                        && email.equalsIgnoreCase(code.optString("email"))) {
                    // The link points at 127.0.0.1 (the computer); from the phone it is 10.0.2.2.
                    String link = code.getString("oobLink")
                            .replace("127.0.0.1", host).replace("localhost", host);
                    get(link);
                    Log.i(TAG, "Verified " + email);
                    return;
                }
            }
            SystemClock.sleep(500);
        }
        throw new AssertionError("No verification email for " + email + " reached the Auth emulator");
    }

    static String uniqueEmail() {
        return String.format(Locale.US, "e2e-%d@plantshome.test", System.currentTimeMillis());
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        try (InputStream in = connection.getResponseCode() < 400
                ? connection.getInputStream() : connection.getErrorStream()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while (in != null && (read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            if (connection.getResponseCode() >= 400) {
                throw new IOException("HTTP " + connection.getResponseCode() + " from " + url + ": "
                        + bytes.toString(StandardCharsets.UTF_8.name()));
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }
}
