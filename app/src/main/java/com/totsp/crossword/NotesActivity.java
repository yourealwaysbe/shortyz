package com.totsp.crossword;

import static com.totsp.crossword.shortyz.ShortyzApplication.BOARD;
import static com.totsp.crossword.shortyz.ShortyzApplication.RENDERER;

import java.io.File;
import java.io.IOException;

import android.os.Bundle;
import android.net.Uri;
import android.widget.EditText;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.WindowManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.InputType;
import android.inputmethodservice.KeyboardView;
import android.util.Log;
import android.content.Intent;
import android.content.res.Configuration;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

import com.totsp.crossword.io.IO;
import com.totsp.crossword.shortyz.R;
import com.totsp.crossword.puz.Playboard.Clue;
import com.totsp.crossword.puz.Puzzle;
import com.totsp.crossword.puz.Note;
import com.totsp.crossword.view.BoardEditText;
import com.totsp.crossword.view.ScrollingImageView;
import com.totsp.crossword.view.ScrollingImageView.ClickListener;
import com.totsp.crossword.puz.Playboard.Word;
import com.totsp.crossword.puz.Playboard.Position;
import com.totsp.crossword.view.ScrollingImageView.Point;
import com.totsp.crossword.puz.Playboard.Word;
import com.totsp.crossword.puz.Box;

public class NotesActivity extends ShortyzKeyboardActivity {

    private final int NO_PREDICT_INPUT
        = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
          InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;

    private ScrollingImageView imageView;

    private BoardEditText scratchView;

	private ImaginaryTimer timer;
	private File baseFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        utils.holographic(this);
		utils.finishOnHomeButton(this);

		try {
			this.configuration = getBaseContext().getResources()
					.getConfiguration();
		} catch (Exception e) {
			Toast.makeText(this, "Unable to read device configuration.",
					Toast.LENGTH_LONG).show();
			finish();
		}

        if(BOARD == null || BOARD.getPuzzle() == null){
            finish();
        }

        timer = new ImaginaryTimer(BOARD.getPuzzle().getTime());
		timer.start();

        Uri u = this.getIntent().getData();

		if (u != null) {
			if (u.getScheme().equals("file")) {
				baseFile = new File(u.getPath());
			}
		}

        setContentView(R.layout.notes);

        createKeyboard((KeyboardView)this.findViewById(R.id.notesKeyboard));

        Clue c = BOARD.getClue();

        boolean showCount = prefs.getBoolean("showCount", false);
        final int curWordLen = BOARD.getCurrentWord().length;

        TextView clue = (TextView) this.findViewById(R.id.clueLine);
        if (clue != null && clue.getVisibility() != View.GONE) {
            clue.setVisibility(View.GONE);
            clue = (TextView) utils.onActionBarCustom(this,
                    R.layout.clue_line_only).findViewById(R.id.clueLine);
        }

        clue.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                              prefs.getInt("clueSize", 12));

        clue.setText("("
                     + (BOARD.isAcross() ? "across" : "down")
                     + ") "
                     + c.number
                     + ". "
                     + c.hint
                     + (showCount ? ("  ["
                     + curWordLen + "]") : ""));

		imageView = (ScrollingImageView) this.findViewById(R.id.miniboard);
		this.imageView.setContextMenuListener(new ClickListener() {
			public void onContextMenu(Point e) {
				// TODO Auto-generated method stub
			}

			public void onTap(Point e) {
                imageView.requestFocus();

				Word current = BOARD.getCurrentWord();
				int newAcross = current.start.across;
				int newDown = current.start.down;
				int box = RENDERER.findBoxNoScale(e);

				if (box < current.length) {
					if (BOARD.isAcross()) {
						newAcross += box;
					} else {
						newDown += box;
					}
				}

                Position newPos = new Position(newAcross, newDown);

				if (!newPos.equals(BOARD.getHighlightLetter())) {
					BOARD.setHighlightLetter(newPos);
					NotesActivity.this.render();
				}
			}
		});

		scratchView = (BoardEditText) this.findViewById(R.id.scratchMiniboard);
        scratchView.setLength(curWordLen);

		Puzzle puz = BOARD.getPuzzle();
        Note note = puz.getNote(c.number, BOARD.isAcross());
        if (note != null) {
            EditText notesBox = (EditText) this.findViewById(R.id.notesBox);
            notesBox.setText(note.getText());
        }

        InputFilter sourceFilter = new InputFilter() {
            public CharSequence filter(CharSequence source,
                                       int start, int end,
                                       Spanned dest, int dstart, int dend) {
                StringBuilder sb = new StringBuilder();
                for (int i = start; i < end; i++) {
                    if (Character.isLetter(source.charAt(i))) {
                        sb.append(Character.toUpperCase(source.charAt(i)));
                    }
                }

                if (source instanceof Spanned) {
                    SpannableString sp = new SpannableString(sb.toString());
                    TextUtils.copySpansFrom((Spanned) source,
                                            start, end, null,
                                            sp, 0);
                    return sp;
                } else {
                    return sb.toString();
                }
            }
        };

        final EditText anagramSource = (EditText) this.findViewById(R.id.anagramSource);
        if (note != null) {
            anagramSource.setText(note.getAnagramSource());
        }
        anagramSource.setFilters(new InputFilter[]{sourceFilter});
        anagramSource.setInputType(NO_PREDICT_INPUT);

        InputFilter solFilter = new InputFilter() {
            public CharSequence filter(CharSequence source,
                                       int start, int end,
                                       Spanned dest, int dstart, int dend) {
                // put everything removed back in the source
                StringBuilder newSource = new StringBuilder();
                newSource.append(anagramSource.getText());
                for (int i = dstart; i < dend; i++) {
                    newSource.append(dest.charAt(i));
                }

                StringBuilder sb = new StringBuilder();
                for (int i = start; i < end; i++) {
                    char c = source.charAt(i);
                    if (Character.isLetter(c)) {
                        char cUp = Character.toUpperCase(source.charAt(i));
                        int pos = newSource.indexOf(String.valueOf(cUp));
                        if (pos >= 0) {
                            newSource.deleteCharAt(pos);
                            sb.append(cUp);
                        }
                    } else if (isAnagramSolutionSpecialChar(c)) {
                        sb.append(c);
                    }
                }

                anagramSource.setText(newSource);

                if (source instanceof Spanned) {
                    SpannableString sp = new SpannableString(sb.toString());
                    TextUtils.copySpansFrom((Spanned) source,
                                            start, end, null,
                                            sp, 0);
                    return sp;
                } else {
                    return sb.toString();
                }
            }
        };

        EditText anagramSol = (EditText) this.findViewById(R.id.anagramSolution);
        if (note != null) {
            anagramSol.setText(note.getAnagramSolution());
        }
        anagramSol.setFilters(new InputFilter[]{solFilter});
        anagramSol.setInputType(NO_PREDICT_INPUT);


        // if not using native keyboard, hide shortyz' when the notesBox is in
        // focus
        OnFocusChangeListener hideKbdListener = new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean gainFocus) {
                setSoftKeyboardVisibility(!gainFocus);
                if (!gainFocus) {
			        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        };

        EditText notesBox = (EditText) this.findViewById(R.id.notesBox);
        notesBox.setOnFocusChangeListener(hideKbdListener);
        anagramSource.setOnFocusChangeListener(hideKbdListener);
        anagramSol.setOnFocusChangeListener(hideKbdListener);

        this.render();
    }

    public void onPause() {
        super.onPause();

        EditText notesBox = (EditText) this.findViewById(R.id.notesBox);
        String text = notesBox.getText().toString();

        EditText anagramSrcBox = (EditText) this.findViewById(R.id.anagramSource);
        String anagramSource = anagramSrcBox.getText().toString();

        EditText anagramSolBox = (EditText) this.findViewById(R.id.anagramSolution);
        String anagramSolution = anagramSolBox.getText().toString();

        Note note = new Note(text, anagramSource, anagramSolution);

        Clue c = BOARD.getClue();
		Puzzle puz = BOARD.getPuzzle();
        puz.setNote(note, c.number, BOARD.isAcross());

		try {
			if ((puz != null) && (baseFile != null)) {
				if ((timer != null) && (puz.getPercentComplete() != 100)) {
					this.timer.stop();
					puz.setTime(timer.getElapsed());
					this.timer = null;
				}

				IO.save(puz, baseFile);
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		if (this.prefs.getBoolean("forceKeyboard", false)
				|| (this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES)
				|| (this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_UNDEFINED)) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(this.imageView.getWindowToken(), 0);
			imm.hideSoftInputFromWindow(this.scratchView.getWindowToken(), 0);
		}
	}



	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            this.finish();
            return true;
        }

        View focused = getWindow().getCurrentFocus();

        switch (focused.getId()) {
        case R.id.miniboard:
            return onMiniboardKeyUp(keyCode, event);

        case R.id.scratchMiniboard:
            return scratchView.onKeyUp(keyCode, event);

        default:
            return false;
        }
    }

    private boolean onMiniboardKeyUp(int keyCode, KeyEvent event) {
		Word w = BOARD.getCurrentWord();
		Position last = new Position(w.start.across
				+ (w.across ? (w.length - 1) : 0), w.start.down
				+ ((!w.across) ? (w.length - 1) : 0));

		switch (keyCode) {
		case KeyEvent.KEYCODE_MENU:
			return false;

        case KeyEvent.KEYCODE_DPAD_LEFT:

			if (!BOARD.getHighlightLetter().equals(
					BOARD.getCurrentWord().start)) {
				BOARD.previousLetter();

				this.render();
			}

			return true;

		case KeyEvent.KEYCODE_DPAD_RIGHT:

			if (!BOARD.getHighlightLetter().equals(last)) {
				BOARD.nextLetter();
				this.render();
			}

			return true;

		case KeyEvent.KEYCODE_DEL:
			w = BOARD.getCurrentWord();
			BOARD.deleteLetter();

			Position p = BOARD.getHighlightLetter();

			if (!w.checkInWord(p.across, p.down)) {
				BOARD.setHighlightLetter(w.start);
			}

			this.render();

			return true;

		case KeyEvent.KEYCODE_SPACE:

			if (!prefs.getBoolean("spaceChangesDirection", true)) {
				BOARD.playLetter(' ');

				Position curr = BOARD.getHighlightLetter();

				if (!BOARD.getCurrentWord().equals(w)
						|| (BOARD.getBoxes()[curr.across][curr.down] == null)) {
					BOARD.setHighlightLetter(last);
				}

				this.render();

				return true;
			}
		}

		char c = Character
				.toUpperCase(((this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) || this.useNativeKeyboard) ? event
						.getDisplayLabel() : ((char) keyCode));

		if (PlayActivity.ALPHA.indexOf(c) != -1) {
			BOARD.playLetter(c);

			Position p = BOARD.getHighlightLetter();

			if (!BOARD.getCurrentWord().equals(w)
					|| (BOARD.getBoxes()[p.across][p.down] == null)) {
				BOARD.setHighlightLetter(last);
			}

			this.render();

            Puzzle puz = BOARD.getPuzzle();
			if ((puz.getPercentComplete() == 100) && (timer != null)) {
	            timer.stop();
	            puz.setTime(timer.getElapsed());
	            this.timer = null;
	            Intent i = new Intent(NotesActivity.this, PuzzleFinishedActivity.class);
	            this.startActivity(i);

	        }

			return true;
		}

		return super.onKeyUp(keyCode, event);
	}


    private void render() {
        renderKeyboard();
		this.imageView.setBitmap(RENDERER.drawWord());
	}

    private static final boolean isAnagramSolutionSpecialChar(char c) {
        switch (c) {
        case ' ':
        case '-':
        case '*':
        case ',':
        case '.':
        case '_':
        case '?':
        case '+':
        case '!':
        case '#':
        case '@':
        case '$':
            return true;
        default:
            return false;
        }
    }
}
