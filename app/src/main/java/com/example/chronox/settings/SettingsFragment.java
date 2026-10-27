package com.example.chronox.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.chronox.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    private SharedPreferences prefs;

    private MaterialCardView cardBlackRed, cardDarkBlue, cardWhiteBlue;
    private ImageView checkBlackRed, checkDarkBlue, checkWhiteBlue;
    private SwitchMaterial switchSound, switchVibration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences(ThemeHelper.PREFS_NAME, Context.MODE_PRIVATE);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        cardBlackRed = view.findViewById(R.id.card_theme_black_red);
        cardDarkBlue = view.findViewById(R.id.card_theme_dark_blue);
        cardWhiteBlue = view.findViewById(R.id.card_theme_white_blue);

        checkBlackRed = view.findViewById(R.id.img_theme_black_red_check);
        checkDarkBlue = view.findViewById(R.id.img_theme_dark_blue_check);
        checkWhiteBlue = view.findViewById(R.id.img_theme_white_blue_check);

        switchSound = view.findViewById(R.id.switch_sound);
        switchVibration = view.findViewById(R.id.switch_vibration);

        // Load current prefs
        int currentTheme = ThemeHelper.getSavedTheme(requireContext());
        updateThemeSelectionUI(currentTheme);

        switchSound.setChecked(prefs.getBoolean(ThemeHelper.KEY_SOUND, true));
        switchVibration.setChecked(prefs.getBoolean(ThemeHelper.KEY_VIBRATION, true));

        // Listeners
        cardBlackRed.setOnClickListener(v -> setTheme(ThemeHelper.THEME_BLACK_RED));
        cardDarkBlue.setOnClickListener(v -> setTheme(ThemeHelper.THEME_DARK_BLUE));
        cardWhiteBlue.setOnClickListener(v -> setTheme(ThemeHelper.THEME_WHITE_BLUE));

        switchSound.setOnCheckedChangeListener((btn, isChecked) -> 
            prefs.edit().putBoolean(ThemeHelper.KEY_SOUND, isChecked).apply());

        switchVibration.setOnCheckedChangeListener((btn, isChecked) -> 
            prefs.edit().putBoolean(ThemeHelper.KEY_VIBRATION, isChecked).apply());
    }

    private void setTheme(int themeId) {
        if (themeId == ThemeHelper.getSavedTheme(requireContext())) return;

        ThemeHelper.saveTheme(requireContext(), themeId);
        updateThemeSelectionUI(themeId);
        
        // Recreate activity to apply theme smoothly. 
        // Timers will restore accurately via timestamp logic in their respective fragments.
        requireActivity().recreate();
    }

    private void updateThemeSelectionUI(int selectedThemeId) {
        checkBlackRed.setVisibility(selectedThemeId == ThemeHelper.THEME_BLACK_RED ? View.VISIBLE : View.INVISIBLE);
        checkDarkBlue.setVisibility(selectedThemeId == ThemeHelper.THEME_DARK_BLUE ? View.VISIBLE : View.INVISIBLE);
        checkWhiteBlue.setVisibility(selectedThemeId == ThemeHelper.THEME_WHITE_BLUE ? View.VISIBLE : View.INVISIBLE);
        
        // Update strokes to highlight selection
        cardBlackRed.setStrokeWidth(selectedThemeId == ThemeHelper.THEME_BLACK_RED ? 4 : 1);
        cardDarkBlue.setStrokeWidth(selectedThemeId == ThemeHelper.THEME_DARK_BLUE ? 4 : 1);
        cardWhiteBlue.setStrokeWidth(selectedThemeId == ThemeHelper.THEME_WHITE_BLUE ? 4 : 1);
    }
}
