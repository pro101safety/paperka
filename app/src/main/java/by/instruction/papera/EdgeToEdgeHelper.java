package by.instruction.papera;

import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Единая настройка edge-to-edge для Android 15+ и обратной совместимости.
 */
public final class EdgeToEdgeHelper {

    private EdgeToEdgeHelper() {
    }

    public static void enable(ComponentActivity activity) {
        EdgeToEdge.enable(activity);
    }

    /** Status bar → верхний padding для toolbar / app bar. */
    public static void applyAppBarInsets(@Nullable View appBar) {
        if (appBar == null) {
            return;
        }
        final int left = appBar.getPaddingLeft();
        final int top = appBar.getPaddingTop();
        final int right = appBar.getPaddingRight();
        final int bottom = appBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(appBar, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(left, top + bars.top, right, bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(appBar);
    }

    /** Navigation bar → нижний padding. */
    public static void applyBottomInsets(@Nullable View view) {
        if (view == null) {
            return;
        }
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(left, top, right, bottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Отступы со всех сторон system bars (экраны без toolbar). */
    public static void applySystemBarsPadding(@Nullable View view) {
        if (view == null) {
            return;
        }
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Drawer NavigationView: не залезать под status/nav bar. */
    public static void applyNavigationViewInsets(@Nullable View navView) {
        if (navView == null) {
            return;
        }
        final int left = navView.getPaddingLeft();
        final int top = navView.getPaddingTop();
        final int right = navView.getPaddingRight();
        final int bottom = navView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(navView, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(left, top + bars.top, right, bottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(navView);
    }

    /** FAB / плавающие элементы: увеличить margin снизу и справа. */
    public static void applyFabMargins(@Nullable View fab) {
        if (fab == null || !(fab.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams initial =
                (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
        final int left = initial.leftMargin;
        final int top = initial.topMargin;
        final int right = initial.rightMargin;
        final int bottom = initial.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(fab, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.leftMargin = left + bars.left;
            lp.topMargin = top;
            lp.rightMargin = right + bars.right;
            lp.bottomMargin = bottom + bars.bottom;
            v.setLayoutParams(lp);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(fab);
    }

    /** Верхний margin для HUD-элементов (таймер игры и т.п.). */
    public static void applyTopMarginInsets(@Nullable View view) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams initial =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int left = initial.leftMargin;
        final int top = initial.topMargin;
        final int right = initial.rightMargin;
        final int bottom = initial.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.leftMargin = left + bars.left;
            lp.topMargin = top + bars.top;
            lp.rightMargin = right + bars.right;
            lp.bottomMargin = bottom;
            v.setLayoutParams(lp);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Отступы для экрана с toolbar.
     * Важно: {@link #enable(ComponentActivity)} вызвать до {@code setContentView}.
     */
    public static void applyToolbarScreenInsets(@Nullable View toolbar,
                                                @Nullable View bottomContent) {
        applyAppBarInsets(toolbar);
        applyBottomInsets(bottomContent);
    }
}
