package com.example.chronox.timer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.chronox.R;
import com.example.chronox.databinding.FragmentTimerContainerBinding;
import com.example.chronox.stopwatch.StopwatchFragment;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * TimerContainerFragment — Host for the two timer features.
 *
 * Uses ViewPager2 + TabLayout to present:
 *   Tab 0: Countdown Timer  (TimerFragment)
 *   Tab 1: Stopwatch        (StopwatchFragment)
 *
 * * The optional "open_tab" argument can select Countdown or Stopwatch.
 *
 * ViewPager2 uses FragmentStateAdapter which properly manages Fragment
 * lifecycle — Fragments are only created when their tab is first shown
 * and destroyed when they scroll far out of view (offscreenPageLimit).
 */
public class TimerContainerFragment extends Fragment {

    private FragmentTimerContainerBinding binding;

    // Tab index constants for readability
    public static final int TAB_COUNTDOWN = 0;
    public static final int TAB_STOPWATCH = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTimerContainerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set up ViewPager2 with the two timer fragments
        TimerPagerAdapter adapter = new TimerPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        // Keep both timer tabs alive in memory so Stopwatch doesn't reset on swipe
        binding.viewPager.setOffscreenPageLimit(2);

        // Link TabLayout to ViewPager2
        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            switch (position) {
                case TAB_COUNTDOWN: tab.setText(R.string.tab_countdown); break;
                case TAB_STOPWATCH: tab.setText(R.string.tab_stopwatch); break;
            }
        }).attach();

        // If launched from HomeFragment quick-nav, open the requested tab
        if (getArguments() != null) {
            int openTab = getArguments().getInt("open_tab", TAB_COUNTDOWN);
            // Validate range before using
            if (openTab >= 0 && openTab < 2) {
                binding.viewPager.setCurrentItem(openTab, false);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * FragmentStateAdapter for the two timer tabs.
     * Creates each Fragment once; ViewPager2 manages their lifecycle.
     */
    private static class TimerPagerAdapter extends FragmentStateAdapter {

        TimerPagerAdapter(Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case TAB_COUNTDOWN: return new TimerFragment();
                case TAB_STOPWATCH: return new StopwatchFragment();
                default:            return new TimerFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
