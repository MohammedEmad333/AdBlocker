package com.example.adblocker;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keep interactive content outside status/navigation bars and display cutouts
 * when targetSdk >= 35 enforces edge-to-edge. Preserve the view's initial
 * padding on every inset dispatch so rotations never accumulate extra space.
 */
final class SystemBarInsets {
    private SystemBarInsets() {}

    static void apply(View root) {
        final int[] initial = {
                root.getPaddingLeft(), root.getPaddingTop(),
                root.getPaddingRight(), root.getPaddingBottom()
        };
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            int[] padding = SystemBarPadding.withInsets(initial,
                    new int[]{bars.left, bars.top, bars.right, bars.bottom});
            view.setPadding(padding[0], padding[1], padding[2], padding[3]);
            // Do not consume IME/other insets needed by descendant scrolling UI.
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
