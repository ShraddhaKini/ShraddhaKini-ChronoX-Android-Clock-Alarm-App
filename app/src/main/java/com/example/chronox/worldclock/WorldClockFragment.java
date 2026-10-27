package com.example.chronox.worldclock;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chronox.R;
import com.example.chronox.common.DialogStyler;
import com.example.chronox.databinding.FragmentWorldClockBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WorldClockFragment — displays a list of world cities with live local times.
 *
 * Features:
 * - Times refresh every 60 seconds
 * - Cities persisted in Room DB, loaded via LiveData (auto-updates RecyclerView)
 * - Long-press → delete confirmation dialog
 * - FAB → city picker dialog (from a curated static list of 50+ cities)
 * - Duplicate prevention (same zoneId cannot be added twice)
 */
public class WorldClockFragment extends Fragment {

    private FragmentWorldClockBinding binding;
    private WorldClockAdapter adapter;
    private WorldClockRepository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Refresh all city times every 60 seconds */
    private final Runnable timeRefresher = new Runnable() {
        @Override
        public void run() {
            if (adapter != null) adapter.refreshTimes();
            handler.postDelayed(this, 60_000L);
        }
    };

    // Curated city → IANA zone ID map
    private static final Map<String, String> CITY_MAP = new LinkedHashMap<>();
    static {
        CITY_MAP.put("New York",       "America/New_York");
        CITY_MAP.put("Los Angeles",    "America/Los_Angeles");
        CITY_MAP.put("Chicago",        "America/Chicago");
        CITY_MAP.put("Toronto",        "America/Toronto");
        CITY_MAP.put("São Paulo",      "America/Sao_Paulo");
        CITY_MAP.put("London",         "Europe/London");
        CITY_MAP.put("Paris",          "Europe/Paris");
        CITY_MAP.put("Berlin",         "Europe/Berlin");
        CITY_MAP.put("Rome",           "Europe/Rome");
        CITY_MAP.put("Madrid",         "Europe/Madrid");
        CITY_MAP.put("Amsterdam",      "Europe/Amsterdam");
        CITY_MAP.put("Stockholm",      "Europe/Stockholm");
        CITY_MAP.put("Moscow",         "Europe/Moscow");
        CITY_MAP.put("Istanbul",       "Europe/Istanbul");
        CITY_MAP.put("Dubai",          "Asia/Dubai");
        CITY_MAP.put("Mumbai",         "Asia/Kolkata");
        CITY_MAP.put("New Delhi",      "Asia/Kolkata");
        CITY_MAP.put("Kolkata",        "Asia/Kolkata");
        CITY_MAP.put("Dhaka",          "Asia/Dhaka");
        CITY_MAP.put("Bangkok",        "Asia/Bangkok");
        CITY_MAP.put("Singapore",      "Asia/Singapore");
        CITY_MAP.put("Kuala Lumpur",   "Asia/Kuala_Lumpur");
        CITY_MAP.put("Jakarta",        "Asia/Jakarta");
        CITY_MAP.put("Hong Kong",      "Asia/Hong_Kong");
        CITY_MAP.put("Shanghai",       "Asia/Shanghai");
        CITY_MAP.put("Beijing",        "Asia/Shanghai");
        CITY_MAP.put("Taipei",         "Asia/Taipei");
        CITY_MAP.put("Seoul",          "Asia/Seoul");
        CITY_MAP.put("Tokyo",          "Asia/Tokyo");
        CITY_MAP.put("Osaka",          "Asia/Tokyo");
        CITY_MAP.put("Sydney",         "Australia/Sydney");
        CITY_MAP.put("Melbourne",      "Australia/Melbourne");
        CITY_MAP.put("Auckland",       "Pacific/Auckland");
        CITY_MAP.put("Honolulu",       "Pacific/Honolulu");
        CITY_MAP.put("Anchorage",      "America/Anchorage");
        CITY_MAP.put("Denver",         "America/Denver");
        CITY_MAP.put("Phoenix",        "America/Phoenix");
        CITY_MAP.put("Vancouver",      "America/Vancouver");
        CITY_MAP.put("Mexico City",    "America/Mexico_City");
        CITY_MAP.put("Bogotá",         "America/Bogota");
        CITY_MAP.put("Lima",           "America/Lima");
        CITY_MAP.put("Buenos Aires",   "America/Argentina/Buenos_Aires");
        CITY_MAP.put("Lagos",          "Africa/Lagos");
        CITY_MAP.put("Cairo",          "Africa/Cairo");
        CITY_MAP.put("Nairobi",        "Africa/Nairobi");
        CITY_MAP.put("Johannesburg",   "Africa/Johannesburg");
        CITY_MAP.put("Riyadh",         "Asia/Riyadh");
        CITY_MAP.put("Karachi",        "Asia/Karachi");
        CITY_MAP.put("Colombo",        "Asia/Colombo");
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWorldClockBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new WorldClockRepository(requireContext());

        // RecyclerView setup
        adapter = new WorldClockAdapter();
        binding.rvWorldClock.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWorldClock.setAdapter(adapter);
        binding.rvWorldClock.setHasFixedSize(false);

        // Long-press → delete
        adapter.setOnDeleteListener(city -> showDeleteDialog(city));

        // Observe cities from DB — RecyclerView updates automatically
        repository.getAllCities().observe(getViewLifecycleOwner(), cities -> {
            adapter.submitList(cities);
            binding.tvEmptyWorldClock.setVisibility(
                    cities == null || cities.isEmpty() ? View.VISIBLE : View.GONE);
        });

        // FAB → add city
        binding.fabAddCity.setOnClickListener(v -> showAddCityDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(timeRefresher);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(timeRefresher);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(timeRefresher);
        binding = null;
    }

    private void showAddCityDialog() {
        List<String> cityNames = new ArrayList<>(CITY_MAP.keySet());
        String[] cities = cityNames.toArray(new String[0]);

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle(R.string.world_clock_add)
                .setItems(cities, (ignored, which) -> {
                    String selectedCity = cities[which];
                    String zoneId = CITY_MAP.get(selectedCity);
                    if (zoneId == null) return;

                    WorldClock city = new WorldClock(selectedCity, zoneId, 0);
                    repository.insert(city, wasInserted -> {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (getContext() == null) return;
                            if (wasInserted) {
                                Toast.makeText(requireContext(),
                                        getString(R.string.world_clock_city_added, selectedCity),
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireContext(),
                                        getString(R.string.world_clock_already_added, selectedCity),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        DialogStyler.show(dialog, requireContext());
    }

    private void showDeleteDialog(WorldClock city) {
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_ChronoX_AlertDialog)
                .setTitle(city.cityName)
                .setMessage(R.string.world_clock_remove)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Activity activity = getActivity();
                    repository.delete(city, () -> {
                        if (activity == null) return;
                        activity.runOnUiThread(() -> {
                            if (!isAdded() || getContext() == null) return;
                            Toast.makeText(requireContext(),
                                    getString(R.string.world_clock_city_removed, city.cityName),
                                    Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        DialogStyler.show(dialog, requireContext());
    }
}
