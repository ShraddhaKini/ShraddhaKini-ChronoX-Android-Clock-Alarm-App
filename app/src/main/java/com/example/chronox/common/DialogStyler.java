package com.example.chronox.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import com.example.chronox.R;

import java.util.WeakHashMap;

/** Applies background blur before a popup is shown and gently enlarges popup text. */
public final class DialogStyler {
    private DialogStyler() {}

    private static final WeakHashMap<View, BlurState> ACTIVE_BLURS = new WeakHashMap<>();

    private static final class BlurState {
        final Object originalEffect;
        int dialogCount;

        BlurState(Object originalEffect) {
            this.originalEffect = originalEffect;
            this.dialogCount = 1;
        }
    }

    /** Call before dialog.show() so blur and dim are prepared in advance of the first visible frame. */
    public static void prepareWindow(Dialog dialog, Context context) {
        if (dialog == null || context == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;

        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.72f);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.72f;
        window.setAttributes(attributes);

        // RenderEffect is applied to the activity content before showing the dialog. Unlike
        // blur-behind window flags, this avoids a visibly delayed blur after the dialog appears.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context instanceof Activity) {
            View content = ((Activity) context).findViewById(android.R.id.content);
            if (content != null) {
                acquireBlur(content, dp(context, 16));
                View dialogDecor = window.getDecorView();
                dialogDecor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    private boolean released;
                    @Override public void onViewAttachedToWindow(View v) { }
                    @Override public void onViewDetachedFromWindow(View v) {
                        if (!released) {
                            released = true;
                            releaseBlur(content);
                            v.removeOnAttachStateChangeListener(this);
                        }
                    }
                });
            }
        }

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int desiredWidth = Math.min(screenWidth - dp(context, 32), dp(context, 480));
        window.setLayout(Math.max(dp(context, 280), desiredWidth), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static synchronized void acquireBlur(View content, float radiusPx) {
        BlurState state = ACTIVE_BLURS.get(content);
        if (state == null) {
            state = new BlurState(null);
            ACTIVE_BLURS.put(content, state);
        } else {
            state.dialogCount++;
        }
        Api31Impl.applyBlur(content, radiusPx);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static synchronized void releaseBlur(View content) {
        BlurState state = ACTIVE_BLURS.get(content);
        if (state == null) return;
        state.dialogCount--;
        if (state.dialogCount <= 0) {
            Api31Impl.restoreRenderEffect(content, state.originalEffect);
            ACTIVE_BLURS.remove(content);
        }
    }

    /** Call right after show (or in an existing onShow listener) to scale popup copy once. */
    public static void enlargeText(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        View decor = dialog.getWindow().getDecorView();
        scaleRecursively(decor);

        if (dialog instanceof AlertDialog) {
            ListView listView = ((AlertDialog) dialog).getListView();
            if (listView != null) {
                // List item views may be inflated just after onShow; scale them as they attach.
                listView.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                    @Override public void onChildViewAdded(View parent, View child) {
                        scaleRecursively(child);
                    }
                    @Override public void onChildViewRemoved(View parent, View child) { }
                });
                scaleRecursively(listView);
            }
        }
    }

    /** Convenience for dialogs without a custom show listener. */
    public static void show(AlertDialog dialog, Context context) {
        prepareWindow(dialog, context);
        dialog.show();
        enlargeText(dialog);
    }

    private static void scaleRecursively(View view) {
        if (view == null) return;
        if (view instanceof TextView) {
            Object scaled = view.getTag(R.id.dialog_text_scaled);
            if (!Boolean.TRUE.equals(scaled)) {
                TextView textView = (TextView) view;
                float currentSp = textView.getTextSize() / view.getResources().getDisplayMetrics().scaledDensity;
                if (currentSp > 0f) {
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                            Math.max(currentSp + 1f, currentSp * 1.08f));
                }
                view.setTag(R.id.dialog_text_scaled, Boolean.TRUE);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scaleRecursively(group.getChildAt(i));
            }
        }
    }


    /** Isolated API-31 calls keep the helper loadable on older supported Android versions. */
    @RequiresApi(Build.VERSION_CODES.S)
    private static final class Api31Impl {
        private Api31Impl() {}

        static void applyBlur(View view, float radiusPx) {
            view.setRenderEffect(RenderEffect.createBlurEffect(
                    radiusPx, radiusPx, Shader.TileMode.CLAMP));
        }

        static void restoreRenderEffect(View view, Object effect) {
            view.setRenderEffect((RenderEffect) effect);
        }
    }

    private static int dp(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}
