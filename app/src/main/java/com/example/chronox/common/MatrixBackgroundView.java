package com.example.chronox.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import com.example.chronox.R;

public class MatrixBackgroundView extends View {

    private Paint linePaint;
    private Paint intersectionPaint;
    private float gridSpacing;
    private float offset = 0;
    private ValueAnimator animator;

    public MatrixBackgroundView(Context context) {
        super(context);
        init(context);
    }

    public MatrixBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MatrixBackgroundView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setStrokeWidth(2f);
        
        intersectionPaint = new Paint();
        intersectionPaint.setAntiAlias(true);
        intersectionPaint.setStyle(Paint.Style.FILL);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorMatrixLine, typedValue, true);
        int color = typedValue.data;
        
        // Solid alpha since colorMatrixLine already contains alpha in themes
        linePaint.setColor(color);
        
        // Intersections slightly more opaque/different
        intersectionPaint.setColor(color);
        // We will increase the alpha slightly for intersections in onDraw if needed, 
        // but let's just use the same color with a wider radius.

        // Determine spacing based on screen density to keep it proportional
        gridSpacing = 24f * getResources().getDisplayMetrics().density;

        // Slow diagonal pan
        animator = ValueAnimator.ofFloat(0, 1f);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setDuration(12000);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            offset = (float) animation.getAnimatedValue() * gridSpacing;
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Draw grid lines
        int numCols = (int) (w / gridSpacing) + 2;
        int numRows = (int) (h / gridSpacing) + 2;

        for (int i = -1; i <= numCols; i++) {
            float x = i * gridSpacing + offset;
            canvas.drawLine(x, 0, x, h, linePaint);
        }

        for (int j = -1; j <= numRows; j++) {
            float y = j * gridSpacing + offset;
            canvas.drawLine(0, y, w, y, linePaint);
        }

        // Draw intersections
        for (int i = -1; i <= numCols; i++) {
            for (int j = -1; j <= numRows; j++) {
                float x = i * gridSpacing + offset;
                float y = j * gridSpacing + offset;
                canvas.drawCircle(x, y, 3f, intersectionPaint);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }
}
