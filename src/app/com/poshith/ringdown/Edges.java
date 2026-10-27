package com.poshith.ringdown;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * Android 15+ draws apps edge to edge (behind the status and navigation bars). Pad the root
 * view by the bars and the keyboard so every screen looks the same as on older Android.
 * Older versions keep their usual layout, so nothing is applied there.
 */
final class Edges {
    private Edges() {}

    static void pad(View root) {
        if (Build.VERSION.SDK_INT < 35) return;
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                android.graphics.Insets bars = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = in.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                return in;
            }
        });
        root.requestApplyInsets();
    }
}
