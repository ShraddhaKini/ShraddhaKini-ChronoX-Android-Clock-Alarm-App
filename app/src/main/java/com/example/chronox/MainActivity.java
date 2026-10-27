package com.example.chronox;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsetsController;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.chronox.common.NotificationHelper;
import com.example.chronox.common.DialogStyler;
import com.example.chronox.timer.TimerCountdownScheduler;
import com.example.chronox.stopwatch.StopwatchStateStore;
import com.example.chronox.databinding.ActivityMainBinding;

/**
 * MainActivity — The single host activity for ChronoX.
 *
 * Responsibilities:
 * 1. Hosts the NavHostFragment which swaps Fragments based on navigation.
 * 2. Connects BottomNavigationView to the NavController with custom NavOptions.
 *    (Custom implementation fixes the bug where re-tapping Clock from World Clock does nothing.)
 * 3. Properly handles system bar insets (status bar + navigation bar) so that:
 *    - Content is never hidden under the status bar.
 *    - Bottom navigation labels are never clipped by the system gesture/nav bar.
 * 4. Requests POST_NOTIFICATIONS permission on Android 13+ at startup.
 *
 * WHY CUSTOM NAVIGATION:
 * NavigationUI.setupWithNavController() has a known issue on flat nav graphs —
 * when tapping a bottom-nav tab that is NOT the current destination, the
 * NavOptions it uses can fail to pop the back stack cleanly when all destinations
 * share the same graph level. We fix this with explicit NavOptions per tap.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    // True while a screen (e.g. a running chess game) wants the full screen.
    private boolean bottomNavHidden = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) promptFullScreenAlarmAccessIfNeeded();
                        // Reconcile the saved deadline even if notifications are denied; the
                        // countdown state must still recover correctly when the app is reopened.
                        TimerCountdownScheduler.restorePendingCountdown(getApplicationContext());
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply user-selected theme from preferences
        com.example.chronox.settings.ThemeHelper.applyTheme(this);

        // ── Enable true edge-to-edge BEFORE setContentView ────────────────
        // This lets our app draw behind the status bar AND navigation bar,
        // giving us full control of insets.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply dark icons off (= light icons on dark background)
        applyDarkSystemBars();

        // Create notification channels early
        NotificationHelper.createNotificationChannels(this);
        // Stopwatch intentionally starts fresh in a new process. Remove any stale notification
        // left by the prior process, but preserve it when this is merely an activity recreation.
        if (!StopwatchStateStore.hasCurrentProcessSession(this)) {
            NotificationHelper.cancelStopwatchNotification(this);
        }

        // Request notification permission on Android 13+
        requestNotificationPermissionIfNeeded();

        // Set up navigation (must come after setContentView)
        setupNavigation();

        // Apply window insets so content doesn't overlap system bars
        applyWindowInsets();
    }

    /**
     * Wires BottomNavigationView → NavController with a custom implementation
     * that fixes the "Clock tab does nothing from World Clock" bug.
     *
     * Root cause of the bug:
     * The default NavigationUI.setupWithNavController() builds NavOptions with
     * popUpTo(startDestination, inclusive=false). When the back stack is
     * [Clock → WorldClock] and you tap Clock, it should pop WorldClock and land
     * on Clock. But in Navigation 2.7.x with a flat graph, this silently fails
     * because NavController sees it as "already at the start" and no-ops.
     *
     * Fix: Use popUpTo(nav_clock, inclusive=true) so we ALWAYS pop all the way
     * to (and including) the Clock screen, then re-add it fresh. This guarantees
     * any tab tap lands correctly, regardless of the current back stack state.
     */
    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment not found in activity_main.xml");
        }

        NavController navController = navHostFragment.getNavController();

        // ── Custom tab selection handler ───────────────────────────────────
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int destId = item.getItemId();

            NavOptions navOptions = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(false)
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), false, false)
                    .setEnterAnim(R.anim.slide_in_right)
                    .setExitAnim(R.anim.slide_out_left)
                    .setPopEnterAnim(R.anim.slide_in_left)
                    .setPopExitAnim(R.anim.slide_out_right)
                    .build();

            try {
                navController.navigate(destId, null, navOptions);
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        // ── Keep selected tab indicator in sync with NavController ─────────
        // When navigation happens via back button or deep link, update the
        // bottom nav highlight to match the current destination.
        navController.addOnDestinationChangedListener((ctrl, destination, args) -> {
            int destId = destination.getId();
            // Only highlight if the destination is a top-level tab
            if (destId == R.id.nav_clock || destId == R.id.nav_alarm
                    || destId == R.id.nav_world_clock || destId == R.id.nav_timer || destId == R.id.nav_chess) {
                binding.bottomNav.getMenu().findItem(destId).setChecked(true);
            }
        });

        // ── Re-select (tap current tab again) → no-op ─────────────────────
        binding.bottomNav.setOnItemReselectedListener(item -> {
            // Do nothing — prevent double-push of the same destination
        });
    }

    /**
     * Shows or hides the bottom navigation bar. When hidden, the current
     * screen expands to use the space the bar occupied.
     */
    public void setBottomNavVisible(boolean visible) {
        if (binding == null || bottomNavHidden == !visible) return;
        bottomNavHidden = !visible;
        binding.bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
        // Re-run the inset listener so the content margin is recalculated.
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    /**
     * Apply window insets to prevent system bars from overlapping the UI.
     *
     * Top inset    → padding on the NavHostFragment (pushes app content below status bar)
     * Bottom inset → padding on the BottomNavigationView (pushes labels above the nav bar)
     *               AND adjust NavHostFragment bottom margin to account for
     *               BottomNav height + system nav bar height.
     *
     * This correctly handles:
     * - Phones with 3-button navigation bar (e.g., ~48dp at the bottom)
     * - Phones with gesture navigation (nav bar is thin ~20dp or zero)
     * - Phones with display cutouts (status bar is taller)
     */
    private void applyWindowInsets() {
        // Bottom nav height in pixels (~80dp with the extra vertical item padding)
        final int bottomNavHeightPx = (int) (80 * getResources().getDisplayMetrics().density);
        final int marginPx = (int) (16 * getResources().getDisplayMetrics().density);

        // Navigation bar   push BottomNav labels above the system nav bar
        // AND adjust the NavHostFragment bottom margin so content isn't hidden under the BottomNav
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top;
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) && imeBottom > 0;

            // When the soft keyboard opens, Android may resize the activity.
            // Keep the app's BottomNavigationView at its original physical
            // bottom position so it does not jump above the keyboard.
            binding.bottomNav.setTranslationY(imeVisible ? imeBottom : 0f);
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams bnLp =
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) binding.bottomNav.getLayoutParams();
            bnLp.bottomMargin = marginPx + navBarHeight;
            bnLp.leftMargin = marginPx;
            bnLp.rightMargin = marginPx;
            binding.bottomNav.setLayoutParams(bnLp);

            // Reset padding just in case
            binding.bottomNav.setPadding(0, 0, 0, 0);

            // NavHostFragment: top padding clears the status bar,
            // bottom margin clears the BottomNav completely
            binding.navHostFragment.setPadding(0, statusBarHeight, 0, 0);
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams lp =
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) binding.navHostFragment.getLayoutParams();
            lp.bottomMargin = (imeVisible || bottomNavHidden)
                    ? 0
                    : bottomNavHeightPx + marginPx + marginPx + navBarHeight;
            binding.navHostFragment.setLayoutParams(lp);

            return insets;
        });
    }

    /**
     * Apply system bar colors.
     * Light themes need dark icons. Dark themes need light icons.
     */
    private void applyDarkSystemBars() {
        boolean isLightTheme = com.example.chronox.settings.ThemeHelper.getSavedTheme(this) == com.example.chronox.settings.ThemeHelper.THEME_WHITE_BLUE;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (isLightTheme) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                } else {
                    controller.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }
            }
        } else {
            // API 29 — legacy flags
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (isLightTheme) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }

        // Status bar color = fully transparent (edge-to-edge — our app background shows through)
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        // Nav bar color = fully transparent (our BottomNav shows through)
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    }

    /**
     * Request POST_NOTIFICATIONS permission on Android 13 (API 33) and newer.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            promptFullScreenAlarmAccessIfNeeded();
            TimerCountdownScheduler.restorePendingCountdown(getApplicationContext());
        }
    }

    /** Android 14+ can block full-screen alarm UIs until the user grants special app access. */
    private void promptFullScreenAlarmAccessIfNeeded() {
        if (Build.VERSION.SDK_INT < 34) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.canUseFullScreenIntent()) return;

        android.content.SharedPreferences accessPrefs =
                getSharedPreferences(com.example.chronox.common.Constants.PREFS_NAME, MODE_PRIVATE);
        if (accessPrefs.getBoolean("full_screen_alarm_access_prompt_shown", false)) return;
        accessPrefs.edit().putBoolean("full_screen_alarm_access_prompt_shown", true).apply();

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                this, R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle("Allow full-screen alarms?")
                .setMessage("To show ChronoX over the lock screen when an alarm rings, enable full-screen alarm access in Android Settings. Without it, Android may show only an expanded notification.")
                .setPositiveButton("Open Settings", (ignored, which) -> {
                    try {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(settings);
                    } catch (Exception ignoredException) {
                        // The regular alarm notification still works if this device lacks that settings page.
                    }
                })
                .setNegativeButton("Later", null)
                .create();
        DialogStyler.show(dialog, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}

