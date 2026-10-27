package com.example.chronox.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.chronox.R;

public class ThemeHelper {
    public static final String PREFS_NAME = "chronox_settings";
    public static final String KEY_THEME = "app_theme";
    public static final String KEY_SOUND = "app_sound";
    public static final String KEY_VIBRATION = "app_vibration";
    
    public static final int THEME_BLACK_RED = 0;
    public static final int THEME_DARK_BLUE = 1;
    public static final int THEME_WHITE_BLUE = 2;

    public static void applyTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeId = prefs.getInt(KEY_THEME, THEME_BLACK_RED);
        
        switch (themeId) {
            case THEME_DARK_BLUE:
                context.setTheme(R.style.Theme_ChronoX_DarkBlue);
                break;
            case THEME_WHITE_BLUE:
                context.setTheme(R.style.Theme_ChronoX_WhiteBlue);
                break;
            case THEME_BLACK_RED:
            default:
                context.setTheme(R.style.Theme_ChronoX_BlackRed);
                break;
        }
    }
    
    public static void saveTheme(Context context, int themeId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, themeId).apply();
    }
    
    public static int getSavedTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME, THEME_BLACK_RED);
    }
}
