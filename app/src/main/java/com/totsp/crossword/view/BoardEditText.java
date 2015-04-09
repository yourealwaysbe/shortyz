package com.totsp.crossword.view;

import static com.totsp.crossword.shortyz.ShortyzApplication.RENDERER;
import static com.totsp.crossword.shortyz.ShortyzApplication.BOARD;

import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.content.res.Configuration;
import android.util.Log;

import com.totsp.crossword.puz.Playboard.Position;
import com.totsp.crossword.puz.Box;
import com.totsp.crossword.PlayActivity;

public class BoardEditText extends ScrollingImageView {
    private Position selection = new Position(-1, 0);
    private Box[] boxes;
    // surely a better way...
    static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private boolean useNativeKeyboard = false;
    protected Configuration configuration;

    // we have our own onTap for input, but the activity containing the widget
    // might also need to know about on taps, so override setContextMenuListener
    // to intercept
    private ClickListener ctxListener;

    public BoardEditText(Context context, AttributeSet as) {
        super(context, as);

        super.setContextMenuListener(new ClickListener() {
            public void onContextMenu(Point e) {
                if (ctxListener != null) {
                    ctxListener.onContextMenu(e);
                }
            }

            public void onTap(Point e) {
                BoardEditText.this.requestFocus();

                int box = RENDERER.findBoxNoScale(e);
                if (box < boxes.length) {
                    selection.across = box;
                }
                BoardEditText.this.render();

                if (ctxListener != null) {
                    ctxListener.onTap(e);
                }
            }
        });

        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean gainFocus) {
                if (!gainFocus) {
                    selection.across = -1;
                    BoardEditText.this.render();
                }
            }
        });

        SharedPreferences prefs
            = PreferenceManager.getDefaultSharedPreferences(context);
        useNativeKeyboard = "NATIVE".equals(prefs.getString("keyboardType", ""));

        configuration = context.getResources().getConfiguration();
    }

    @Override
    public void setContextMenuListener(ClickListener l) {
        this.ctxListener = l;
    }

    public void setLength(int len) {
        Box[] newBoxes = new Box[len];

        int overlap = 0;
        if (boxes != null) {
            overlap = Math.min(len, boxes.length);
            for (int i = 0; i < overlap; i++) {
                newBoxes[i] = boxes[i];
            }
        }

        for (int i = overlap; i < len; ++i) {
            newBoxes[i] = new Box();
        }

        boxes = newBoxes;

        render();
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_MENU:
			return false;

        case KeyEvent.KEYCODE_DPAD_LEFT:
			if (selection.across > 0) {
				selection.across--;
				this.render();
			}
			return true;

		case KeyEvent.KEYCODE_DPAD_RIGHT:
			if (selection.across < boxes.length - 1) {
				selection.across++;
				this.render();
			}

			return true;

		case KeyEvent.KEYCODE_DEL:
            boxes[selection.across].setResponse(' ');
            if (selection.across > 0) {
                selection.across--;
                this.render();
            }
            return true;

		case KeyEvent.KEYCODE_SPACE:
            boxes[selection.across].setResponse(' ');

            if (selection.across < boxes.length - 1) {
                selection.across++;

                while (BOARD.isSkipCompletedLetters() &&
                       boxes[selection.across].getResponse() != ' ' &&
                       selection.across < boxes.length - 1) {
                    selection.across++;
                }
				this.render();
            }
            return true;
        }

		char c = Character
				.toUpperCase(((this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) || this.useNativeKeyboard) ? event
						.getDisplayLabel() : ((char) keyCode));

		if (ALPHA.indexOf(c) != -1) {
			boxes[selection.across].setResponse(c);

            if (selection.across < boxes.length - 1) {
                selection.across++;

                while (BOARD.isSkipCompletedLetters() &&
                       boxes[selection.across].getResponse() != ' ' &&
                       selection.across < boxes.length - 1) {
                    selection.across++;
                }
            }

            this.render();

            return true;
		}

		return super.onKeyUp(keyCode, event);
	}

    private void render() {
        setBitmap(RENDERER.drawBoxes(boxes, selection));
    }

}
