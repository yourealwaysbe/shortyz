package com.totsp.crossword.view;

import static com.totsp.crossword.shortyz.ShortyzApplication.RENDERER;
import static com.totsp.crossword.shortyz.ShortyzApplication.BOARD;

import java.lang.StringBuilder;

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

    public interface BoardEditFilter {
        /**
         * @param oldChar the character being deleted
         * @param pos the position of the box being deleted from
         * @return true if the deletion is allowed to occur
         */
        public boolean delete(char oldChar, int pos);

        /**
         * @param oldChar the character that used to be in the box
         * @param newChar the character to replace it with
         * @param pos the position of the box
         * @return the actual character to replace the old one with or null char
         * if the replacement is not allowed
         */
        public char filter(char oldChar, char newChar, int pos);
    }

    private Position selection = new Position(-1, 0);
    private Box[] boxes;
    // surely a better way...
    static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private boolean useNativeKeyboard = false;
    private Configuration configuration;

    // we have our own onTap for input, but the activity containing the widget
    // might also need to know about on taps, so override setContextMenuListener
    // to intercept
    private ClickListener ctxListener;
    private BoardEditFilter[] filters;

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
                if (boxes != null && box < boxes.length) {
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

    public void setFilters(BoardEditFilter[] filters) {
        this.filters = filters;
    }

    public void setLength(int len) {
        if (boxes == null || len != boxes.length) {
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
    }

    public char getResponse(int pos) {
        if (0 <= pos && pos < boxes.length) {
            return boxes[pos].getResponse();
        } else {
            return '\0';
        }
    }

    public void setResponse(int pos, char c) {
        if (0 <= pos && pos < boxes.length) {
            boxes[pos].setResponse(c);
            render();
        }
    }

    public void setFromString(String text) {
        if (text == null) {
            boxes = null;
        } else {
            boxes = new Box[text.length()];
            for (int i = 0; i < text.length(); i++) {
                boxes[i] = new Box();
                boxes[i].setResponse(text.charAt(i));
            }
        }
        render();
    }

    public String toString() {
        if (boxes == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < boxes.length; i++) {
            sb.append(boxes[i].getResponse());
        }

        return sb.toString();
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
			if (boxes != null && selection.across < boxes.length - 1) {
				selection.across++;
				this.render();
			}

			return true;

		case KeyEvent.KEYCODE_DEL:
            if (boxes != null && canDelete(selection)) {
                boxes[selection.across].setResponse(' ');
                if (selection.across > 0) {
                    selection.across--;
                }
                this.render();
            }
            return true;

		case KeyEvent.KEYCODE_SPACE:
            if (boxes != null && canDelete(selection)) {
                boxes[selection.across].setResponse(' ');

                if (selection.across < boxes.length - 1) {
                    selection.across++;

                    while (BOARD.isSkipCompletedLetters() &&
                           boxes[selection.across].getResponse() != ' ' &&
                           selection.across < boxes.length - 1) {
                        selection.across++;
                    }
                }

                this.render();
            }
            return true;
        }

		char c = Character
				.toUpperCase(((this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) || this.useNativeKeyboard) ? event
						.getDisplayLabel() : ((char) keyCode));

		if (boxes != null && ALPHA.indexOf(c) != -1) {
            c = filterReplacement(c, selection);

            if (c != '\0') {
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
            }

            return true;
		}

		return super.onKeyUp(keyCode, event);
	}

    private void render() {
        setBitmap(RENDERER.drawBoxes(boxes, selection));
    }

    private boolean canDelete(Position pos) {
        if (filters == null)
            return true;

        char oldChar = boxes[pos.across].getResponse();

        for (BoardEditFilter filter : filters) {
            if (filter != null && !filter.delete(oldChar, pos.across)) {
                return false;
            }
        }

        return true;
    }

    private char filterReplacement(char newChar, Position pos) {
        if (filters == null)
            return newChar;

        char oldChar = boxes[pos.across].getResponse();

        for (BoardEditFilter filter : filters) {
            if (filter != null) {
                newChar = filter.filter(oldChar, newChar, pos.across);
            }
        }

        return newChar;
    }
}
