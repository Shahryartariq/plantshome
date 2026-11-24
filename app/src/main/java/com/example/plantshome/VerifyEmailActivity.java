package com.example.plantshome;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.example.plantshome.databinding.ActivityVerifyemailBinding;
import com.example.plantshome.util.AppNavigator;
import com.example.plantshome.util.AuthErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Gate between sign in and the app: the user stays here until their email is verified.
 */
public class VerifyEmailActivity extends BaseActivity {

    private static final long RECHECK_MS = 4000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable checkVerified = this::checkIfVerified;
    private boolean visible;

    private ActivityVerifyemailBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            AppNavigator.signOut(this);
            return;
        }
        binding = ActivityVerifyemailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.logOut.setOnClickListener(v -> AppNavigator.signOut(this));
        binding.verifyBtn.setOnClickListener(v -> sendVerificationEmail());
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        // The user may open the link on any device: check now and every few seconds while visible.
        checkVerified.run();
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(checkVerified);
        super.onPause();
    }

    private void checkIfVerified() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return;
        }
        user.reload().addOnCompleteListener(this, task -> {
            FirebaseUser fresh = auth.getCurrentUser();
            if (task.isSuccessful() && fresh != null && fresh.isEmailVerified()) {
                AppNavigator.routeSignedInUser(this);
            } else if (visible && !isFinishing()) {
                handler.removeCallbacks(checkVerified);
                handler.postDelayed(checkVerified, RECHECK_MS);
            }
        });
    }

    private void sendVerificationEmail() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            AppNavigator.signOut(this);
            return;
        }
        binding.verifyBtn.setEnabled(false);
        user.sendEmailVerification()
                .addOnSuccessListener(this, unused -> toast(R.string.message_verification_sent))
                .addOnFailureListener(this, error -> toast(AuthErrors.message(this, error)))
                .addOnCompleteListener(this, task -> binding.verifyBtn.setEnabled(true));
    }
}
