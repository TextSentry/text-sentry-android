package org.textsentry.app;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.inspector.WindowInspector;
import android.widget.Switch;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;

@SdkSuppress(minSdkVersion = 29)
public class HomeStopRepliesTest {
    private final Instrumentation device = InstrumentationRegistry.getInstrumentation();

    @Test public void homeSwitchRequiresConsentAndTurnsOffOnlyReplies() throws Exception {
        Context app = device.getTargetContext();
        Settings settings = new Settings(app);
        KeyVault vault = new KeyVault(app);
        vault.save("synthetic-home-control-test");
        SetupFlow.complete(app);
        settings.startMonitoring();
        device.getUiAutomation().grantRuntimePermission(app.getPackageName(), Manifest.permission.RECEIVE_SMS);
        device.getUiAutomation().grantRuntimePermission(app.getPackageName(), Manifest.permission.SEND_SMS);
        Activity home = device.startActivitySync(new Intent(app, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            Switch toggle = home.findViewById(R.id.stop_replies);
            assertNotNull(toggle);
            assertFalse(toggle.isChecked());
            click(toggle);
            click(dialogButton(android.R.id.button2));
            assertFalse(settings.autoSend());
            click(home.findViewById(R.id.stop_replies));
            click(dialogButton(android.R.id.button1));
            assertTrue(settings.autoSend());
            toggle = home.findViewById(R.id.stop_replies);
            assertTrue(toggle.isChecked());
            if (android.os.Build.VERSION.SDK_INT >= 34
                    && "true".equals(InstrumentationRegistry.getArguments().getString("captureHome"))) {
                Bitmap screenshot = device.getUiAutomation().takeScreenshot(home.getWindow());
                assertNotNull("Home window should be rendered", screenshot);
                try (FileOutputStream file = new FileOutputStream(new File(app.getCacheDir(), "home.png"))) {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, file));
                } finally {
                    screenshot.recycle();
                }
            }
            String generation = settings.generation();
            click(toggle);
            assertFalse(settings.autoSend());
            assertTrue(settings.monitoring());
            assertTrue(settings.mmsEnabled());
            assertNotEquals(generation, settings.generation());
            assertFalse(((Switch) home.findViewById(R.id.stop_replies)).isChecked());
        } finally {
            device.runOnMainSync(home::finish);
            settings.pause();
            vault.clear();
            SetupFlow.reset(app);
        }
    }

    private View dialogButton(int id) {
        for (View window : WindowInspector.getGlobalWindowViews()) {
            View button = window.findViewById(id);
            if (button != null) return button;
        }
        throw new AssertionError("Expected a confirmation dialog");
    }

    private void click(View control) {
        device.runOnMainSync(control::performClick);
        device.waitForIdleSync();
    }
}
