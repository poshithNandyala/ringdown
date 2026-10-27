package com.poshith.ringdown;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Lays children out left to right and wraps to a new row when the line is full (chips). */
final class FlowLayout extends ViewGroup {
    private final int hGap, vGap;

    FlowLayout(Context c, int hGap, int vGap) {
        super(c);
        this.hGap = hGap;
        this.vGap = vGap;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int avail = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int x = 0, rowH = 0, h = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            v.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = v.getMeasuredWidth();
            if (x > 0 && x + w > avail) {
                h += rowH + vGap;
                x = 0;
                rowH = 0;
            }
            x += w + hGap;
            rowH = Math.max(rowH, v.getMeasuredHeight());
        }
        h += rowH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, resolveSize(h, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int avail = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = getPaddingTop(), rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int w = v.getMeasuredWidth(), vh = v.getMeasuredHeight();
            if (x > 0 && x + w > avail) {
                y += rowH + vGap;
                x = 0;
                rowH = 0;
            }
            int left = getPaddingLeft() + x;
            v.layout(left, y, left + w, y + vh);
            x += w + hGap;
            rowH = Math.max(rowH, vh);
        }
    }
}
