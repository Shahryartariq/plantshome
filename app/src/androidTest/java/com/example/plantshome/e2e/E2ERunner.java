package com.example.plantshome.e2e;

import android.app.Application;
import android.os.Bundle;

import androidx.test.runner.AndroidJUnitRunner;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Test runner that can point Firebase Auth at the local Auth emulator, so the end-to-end test
 * signs up and logs in for real without touching the production Firebase project.
 *
 * <p>Pass {@code -e authEmulatorHost 10.0.2.2} (the computer running the Android emulator).
 */
public class E2ERunner extends AndroidJUnitRunner {

    static final int AUTH_EMULATOR_PORT = 9099;
    private static String authEmulatorHost;

    /** Host of the Auth emulator, or null when the tests run against real Firebase. */
    static String authEmulatorHost() {
        return authEmulatorHost;
    }

    @Override
    public void onCreate(Bundle arguments) {
        authEmulatorHost = arguments == null ? null : arguments.getString("authEmulatorHost");
        super.onCreate(arguments);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        super.callApplicationOnCreate(app);
        if (authEmulatorHost != null && !authEmulatorHost.isEmpty()) {
            FirebaseAuth.getInstance().useEmulator(authEmulatorHost, AUTH_EMULATOR_PORT);
        }
    }
}
