package com.totsp.crossword;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.widget.Toast;

import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.KeyEvent;
import android.content.res.Configuration;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.os.Handler;

import com.totsp.crossword.shortyz.R;

import com.totsp.crossword.versions.AndroidVersionUtils;

public class ShortyzKeyboardActivity extends ShortyzActivity {
    // migrate to private?
    protected KeyboardView keyboardView = null;
    // migrate to private
    protected boolean useNativeKeyboard = false;
    protected Configuration configuration;

    private Handler handler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		try {
			this.configuration = getBaseContext().getResources()
					.getConfiguration();
		} catch (Exception e) {
			Toast.makeText(this, "Unable to read device configuration.",
					Toast.LENGTH_LONG).show();
			finish();
            return;
		}
    }

	protected void createKeyboard(KeyboardView kbdView) {
        this.keyboardView = kbdView;

        View.OnKeyListener onKeyListener = new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    return ShortyzKeyboardActivity.this.onKeyUp(i, keyEvent);
                } else {
                    return false;
                }
            }
        };

        int keyboardType = "CONDENSED_ARROWS".equals(prefs.getString(
                "keyboardType", "")) ? R.xml.keyboard_dpad : R.xml.keyboard;
        Keyboard keyboard = new Keyboard(this, keyboardType);
        keyboardView.setKeyboard(keyboard);
        this.useNativeKeyboard = "NATIVE".equals(prefs.getString(
                "keyboardType", ""));

        if (this.useNativeKeyboard) {
            keyboardView.setVisibility(View.GONE);
        }
        keyboardView.setOnKeyListener(onKeyListener);
        keyboardView
                .setOnKeyboardActionListener(new OnKeyboardActionListener() {
                    private long lastSwipe = 0;

                    public void onKey(int primaryCode, int[] keyCodes) {
                        long eventTime = System.currentTimeMillis();

                        if (keyboardView.getVisibility() == View.GONE || (eventTime - lastSwipe) < 500) {
                            return;
                        }

                        KeyEvent event = new KeyEvent(eventTime, eventTime,
                                KeyEvent.ACTION_UP, primaryCode, 0, 0, 0,
                                0, KeyEvent.FLAG_SOFT_KEYBOARD
                                | KeyEvent.FLAG_KEEP_TOUCH_MODE);
                        ShortyzKeyboardActivity.this.onKeyUp(primaryCode, event);
                    }

                    public void onPress(int primaryCode) {
                    }

                    public void onRelease(int primaryCode) {
                    }

                    public void onText(CharSequence text) {
                        // TODO Auto-generated method stub
                    }

                    public void swipeDown() {
                        long eventTime = System.currentTimeMillis();
                        lastSwipe = eventTime;

                        KeyEvent event = new KeyEvent(eventTime, eventTime,
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_DOWN, 0, 0, 0, 0,
                                KeyEvent.FLAG_SOFT_KEYBOARD
                                        | KeyEvent.FLAG_KEEP_TOUCH_MODE);
                        ShortyzKeyboardActivity.this.onKeyUp(KeyEvent.KEYCODE_DPAD_DOWN, event);
                    }

                    public void swipeLeft() {
                        long eventTime = System.currentTimeMillis();
                        lastSwipe = eventTime;

                        KeyEvent event = new KeyEvent(eventTime, eventTime,
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT, 0, 0, 0, 0,
                                KeyEvent.FLAG_SOFT_KEYBOARD
                                        | KeyEvent.FLAG_KEEP_TOUCH_MODE);
                        ShortyzKeyboardActivity.this.onKeyUp(KeyEvent.KEYCODE_DPAD_LEFT, event);
                    }

                    public void swipeRight() {
                        long eventTime = System.currentTimeMillis();
                        lastSwipe = eventTime;

                        KeyEvent event = new KeyEvent(eventTime, eventTime,
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0, 0, 0,
                                KeyEvent.FLAG_SOFT_KEYBOARD
                                        | KeyEvent.FLAG_KEEP_TOUCH_MODE);
                        ShortyzKeyboardActivity.this.onKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT, event);
                    }

                    public void swipeUp() {
                        long eventTime = System.currentTimeMillis();
                        lastSwipe = eventTime;

                        KeyEvent event = new KeyEvent(eventTime, eventTime,
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_UP, 0, 0, 0, 0,
                                KeyEvent.FLAG_SOFT_KEYBOARD
                                        | KeyEvent.FLAG_KEEP_TOUCH_MODE);
                        ShortyzKeyboardActivity.this.onKeyUp(KeyEvent.KEYCODE_DPAD_UP, event);
                    }
                });
	}


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        this.configuration = newConfig;
        renderKeyboard();
    }

    protected void renderKeyboard() {
        if (this.prefs.getBoolean("forceKeyboard", false)
                || (configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES)
                || (configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_UNDEFINED)) {
            if (this.useNativeKeyboard) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,
                        InputMethodManager.HIDE_IMPLICIT_ONLY);
            } else {
                this.keyboardView.setVisibility(View.VISIBLE);
            }
        } else {
            this.keyboardView.setVisibility(View.GONE);
        }
    }

    protected void setSoftKeyboardVisibility(boolean show) {
        if (!this.useNativeKeyboard) {
            if (show) {
                this.keyboardView.setVisibility(View.VISIBLE);
            } else {
                this.keyboardView.setVisibility(View.GONE);
            }
        }
    }

    protected void onResumeKeyboard(KeyboardView kbdView) {
        this.keyboardView = kbdView;

        int keyboardType = "CONDENSED_ARROWS".equals(prefs.getString(
                "keyboardType", "")) ? R.xml.keyboard_dpad : R.xml.keyboard;
        final Keyboard keyboard = new Keyboard(this, keyboardType);
        this.keyboardView.setKeyboard(keyboard);
        this.useNativeKeyboard = "NATIVE".equals(prefs.getString(
                "keyboardType", ""));

        if (this.useNativeKeyboard) {
            keyboardView.setVisibility(View.GONE);
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                keyboardView.invalidate();
                keyboardView.invalidateAllKeys();
            }
        });

    }
}
