package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Consistent native surfaces; no WebView, framework or remote UI assets. */
public final class UiKit {
    private UiKit() {}
    // Keep in sync with website :root tokens in src/assets/site.css.
    public static final int NIGHT = Color.rgb(5, 14, 32);
    public static final int INK = Color.rgb(11, 23, 48);
    public static final int MUTED = Color.rgb(93, 111, 136);
    public static final int ACCENT = Color.rgb(38, 215, 238);
    public static final int BG = Color.rgb(243, 247, 255);
    public static final int HIGHLIGHT = Color.rgb(231, 240, 255);

    public static int dp(Activity a, int n) {
        return Math.round(n * a.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable shape(int color, int radius, int border) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        if (border != 0) bg.setStroke(1, border);
        return bg;
    }

    public static LinearLayout vertical(Activity a) {
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.VERTICAL);
        return line;
    }

    private static void styleSystemBars(Activity a, boolean dark) {
        Window window = a.getWindow();
        // The theme provides these colors even before onCreate draws a single frame.
        // Android 15+ enforces edge-to-edge, so a permanent navy overlay is drawn below.
        if (Build.VERSION.SDK_INT < 35) {
            window.setStatusBarColor(NIGHT);
            window.setNavigationBarColor(dark ? NIGHT : BG);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility()
                    & ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            decor.setSystemUiVisibility(dark ? flags : flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    public static LinearLayout screen(Activity a, String title, boolean back) {
        return screen(a, title, back, false);
    }

    static LinearLayout home(Activity a) {
        return screen(a, "", false, true);
    }

    private static LinearLayout screen(Activity a, String title, boolean back, boolean home) {
        styleSystemBars(a, home);
        FrameLayout frame = new FrameLayout(a);
        if (home) {
            frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{NIGHT, 0xFF10243B, 0xFF12182F, NIGHT}));
        } else {
            frame.setBackgroundColor(BG);
        }
        LinearLayout page = vertical(a);
        frame.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolbar = row(a);
        toolbar.setMinimumHeight(dp(a, 64));
        toolbar.setBackgroundColor(NIGHT);
        toolbar.setPaddingRelative(dp(a, back ? 8 : 20), 0, dp(a, 12), 0);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        if (back) {
            iconButton(a, toolbar, R.drawable.ic_back, "Back", Color.WHITE, a::finish);
        } else {
            ImageView icon = new ImageView(a);
            icon.setImageResource(R.mipmap.ic_launcher);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams logo = new LinearLayout.LayoutParams(dp(a, 34), dp(a, 34));
            logo.setMarginEnd(dp(a, 10));
            toolbar.addView(icon, logo);
        }
        TextView heading = text(a, title.isEmpty() ? "TextSentry" : title, 21, Color.WHITE, true);
        heading.setLetterSpacing(-0.025f);
        if (title.isEmpty()) {
            SpannableString brand = new SpannableString("TextSentry");
            brand.setSpan(new ForegroundColorSpan(ACCENT), 4, brand.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            heading.setText(brand);
        }
        markHeading(heading);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        if (home) {
            iconButton(a, toolbar, R.drawable.ic_history, "History", Color.WHITE,
                    () -> a.startActivity(new Intent(a, HistoryActivity.class))).setId(R.id.open_history);
            iconButton(a, toolbar, R.drawable.ic_settings, "Settings", Color.WHITE,
                    () -> a.startActivity(new Intent(a, SettingsActivity.class))).setId(R.id.open_settings);
        }

        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        FrameLayout scrollContent = new FrameLayout(a);
        scroll.addView(scrollContent);
        LinearLayout content = new LinearLayout(a) {
            @Override protected void onMeasure(int width, int height) {
                int maxWidth = dp(a, 600);
                super.onMeasure(MeasureSpec.makeMeasureSpec(
                        Math.min(MeasureSpec.getSize(width), maxWidth), MeasureSpec.EXACTLY), height);
            }
        };
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPaddingRelative(dp(a, 20), dp(a, 24), dp(a, 20), dp(a, 28));
        int alignment = home ? Gravity.CENTER : Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        scrollContent.addView(content, new FrameLayout.LayoutParams(-1, -2, alignment));

        // Android 15+ requires an in-app scrim and explicit insets for edge-to-edge.
        View statusScrim = new View(a);
        statusScrim.setBackgroundColor(NIGHT);
        frame.addView(statusScrim, new FrameLayout.LayoutParams(-1, 0, Gravity.TOP));
        a.setContentView(frame);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = a.getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        home ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
            frame.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int bottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
                page.setPadding(bars.left, bars.top, bars.right, bottom);
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) statusScrim.getLayoutParams();
                if (params.height != bars.top) {
                    params.height = bars.top;
                    statusScrim.setLayoutParams(params);
                }
                return insets;
            });
            frame.requestApplyInsets();
        }
        return content;
    }

    static LinearLayout brandPanel(Activity a, LinearLayout parent, String title, String description) {
        LinearLayout panel = card(a, parent, NIGHT);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF123149, NIGHT, 0xFF27234B});
        background.setCornerRadius(dp(a, 24));
        panel.setBackground(background);

        brandIntro(a, panel, title, description);
        return panel;
    }

    static void brandIntro(Activity a, LinearLayout panel, String title, String description) {
        FrameLayout illustration = new FrameLayout(a);
        illustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        panel.addView(illustration, new LinearLayout.LayoutParams(-1, dp(a, 96)));
        ImageView paths = new ImageView(a);
        paths.setImageResource(R.drawable.brand_signal);
        paths.setScaleType(ImageView.ScaleType.FIT_CENTER);
        illustration.addView(paths, new FrameLayout.LayoutParams(-1, -1));
        ImageView mark = new ImageView(a);
        mark.setImageResource(R.drawable.ic_textsentry_foreground);
        illustration.addView(mark, new FrameLayout.LayoutParams(dp(a, 92), dp(a, 92), Gravity.CENTER));

        TextView headline = title(a, panel, title);
        headline.setPadding(0, dp(a, 12), 0, 0);
        headline.setTextColor(Color.WHITE);
        TextView detail = description(a, panel, description);
        detail.setTextColor(0xFFCDDCED);
        detail.setTextSize(15);
    }

    static ScrollView scrollable(Activity a, View content) {
        ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        return scroll;
    }

    static LinearLayout row(Activity a) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static RippleDrawable interaction(Activity a, int color, int radius, int border) {
        int rounded = dp(a, radius);
        int ink = color == NIGHT || color == Color.TRANSPARENT ? 0x2249A8DC : 0x22145180;
        return new RippleDrawable(ColorStateList.valueOf(ink),
                shape(color, rounded, border), shape(Color.WHITE, rounded, 0));
    }

    private static ImageButton iconButton(Activity a, LinearLayout parent, int icon,
                                          String label, int tint, Runnable action) {
        ImageButton button = new ImageButton(a);
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(tint));
        button.setBackground(interaction(a, Color.TRANSPARENT, 24, 0));
        button.setContentDescription(label);
        button.setTooltipText(label);
        parent.addView(button, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    static ImageButton sectionHelp(Activity a, LinearLayout parent, String title, Runnable help) {
        LinearLayout header = row(a);
        parent.addView(header);
        TextView label = text(a, title, 18, INK, true);
        markHeading(label);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        return iconButton(a, header, R.drawable.ic_info, "About " + title, MUTED, help);
    }

    static void info(Activity a, String title, String message) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(message)
                .setPositiveButton("Done", null).show();
    }

    private static void markHeading(TextView view) {
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
    }

    static TextView title(Activity a, LinearLayout parent, String title) {
        TextView heading = text(a, title, 30, INK, true);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setLetterSpacing(-0.035f);
        markHeading(heading);
        parent.addView(heading);
        return heading;
    }

    static void link(Activity a, LinearLayout parent, String title, Runnable action) {
        Button link = new Button(a);
        link.setText(title);
        link.setTextSize(15);
        link.setAllCaps(false);
        link.setTextColor(INK);
        link.setMinHeight(dp(a, 48));
        link.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        link.setPaddingRelative(0, dp(a, 8), dp(a, 6), dp(a, 8));
        link.setBackground(interaction(a, Color.TRANSPARENT, 10, 0));
        android.graphics.drawable.Drawable arrow = a.getDrawable(R.drawable.ic_chevron);
        if (arrow != null) {
            arrow.setTint(MUTED);
            arrow.setAutoMirrored(true);
            link.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrow, null);
        }
        parent.addView(link, new LinearLayout.LayoutParams(-1, -2));
        link.setOnClickListener(v -> action.run());
    }

    public static TextView text(Activity a, String value, float size, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(dp(a, 2), 1.08f);
        if (bold) {
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setLetterSpacing(-0.015f);
        }
        return t;
    }

    public static LinearLayout card(Activity a, LinearLayout parent, int background) {
        LinearLayout item = vertical(a);
        item.setPadding(dp(a, 20), dp(a, 21), dp(a, 20), dp(a, 21));
        item.setElevation(dp(a, 2));
        item.setBackground(shape(background, dp(a, 19), background == Color.WHITE ? 0xFFDEE7F7 : 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(a, 15);
        parent.addView(item, lp);
        return item;
    }

    public static TextView heading(Activity a, LinearLayout parent, String title) {
        TextView t = text(a, title, 19, INK, true);
        markHeading(t);
        parent.addView(t);
        return t;
    }

    public static TextView description(Activity a, LinearLayout parent, String value) {
        TextView t = text(a, value, 13, MUTED, false);
        t.setPadding(0, dp(a, 7), 0, dp(a, 6));
        parent.addView(t);
        return t;
    }

    public static EditText input(Activity a, LinearLayout parent, String label,
                                 String value, boolean multi, boolean secret) {
        TextView l = text(a, label, 13, INK, true);
        l.setPadding(0, dp(a, 16), 0, dp(a, 7));
        parent.addView(l);
        EditText edit = new EditText(a);
        edit.setTextSize(15);
        edit.setTextColor(INK);
        edit.setSingleLine(!multi);
        edit.setPadding(dp(a, 12), dp(a, 11), dp(a, 12), dp(a, 11));
        edit.setBackground(shape(Color.WHITE, dp(a, 9), 0xFFDEE7F7));
        if (multi) {
            edit.setMinLines(4);
            edit.setMaxLines(10);
            edit.setGravity(Gravity.TOP);
        }
        if (secret) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setText(value);
        edit.setId(View.generateViewId());
        edit.setSaveEnabled(false);
        l.setLabelFor(edit.getId());
        parent.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return edit;
    }

    public static Button button(Activity a, LinearLayout parent, String title,
                                boolean primary, Runnable action) {
        Button b = new Button(a);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{MUTED, primary ? NIGHT : INK}));
        if (primary) {
            GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFF60E6E7, ACCENT, 0xFF78BEFF});
            fill.setCornerRadius(dp(a, 16));
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33145180), fill, null));
        } else {
            b.setBackground(interaction(a, HIGHLIGHT, 16, 0));
        }
        b.setMinHeight(dp(a, 52));
        b.setPaddingRelative(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(a, 12);
        parent.addView(b, lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    /** Google-approved light treatment: standard four-color G, bordered white surface. */
    public static LinearLayout googleSignInButton(Activity a, LinearLayout parent,
                                                  Runnable action) {
        LinearLayout surface = new LinearLayout(a);
        surface.setOrientation(LinearLayout.HORIZONTAL);
        surface.setGravity(Gravity.CENTER);
        surface.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        surface.setMinimumHeight(dp(a, 56));
        surface.setBackground(interaction(a, Color.WHITE, 14, 0xFF747775));
        surface.setClickable(true);
        surface.setFocusable(true);
        surface.setContentDescription("Sign in with Google");
        android.widget.ImageView google = new android.widget.ImageView(a);
        google.setImageResource(R.drawable.ic_google_g);
        surface.addView(google, new LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)));
        TextView label = text(a, "Sign in with Google", 15, 0xFF1F1F1F, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.leftMargin = dp(a, 10);
        surface.addView(label, labelParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(a, 8);
        parent.addView(surface, params);
        surface.setOnClickListener(v -> action.run());
        return surface;
    }

    public static TextView chip(Activity a, LinearLayout parent, String value) {
        TextView t = text(a, value, 12, 0xFF145180, true);
        t.setBackground(shape(0xFFCCF8FF, dp(a, 20), 0));
        t.setPadding(dp(a, 12), dp(a, 8), dp(a, 12), dp(a, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(a, 12);
        parent.addView(t, lp);
        return t;
    }
}
