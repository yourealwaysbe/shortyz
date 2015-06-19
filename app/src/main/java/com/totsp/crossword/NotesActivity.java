package com.totsp.crossword;

import static com.totsp.crossword.shortyz.ShortyzApplication.BOARD;
import static com.totsp.crossword.shortyz.ShortyzApplication.RENDERER;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import android.os.Bundle;
import android.net.Uri;
import android.widget.EditText;
import android.widget.TextView;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.inputmethodservice.KeyboardView;
import android.util.Log;
import android.content.Intent;
import android.content.res.Configuration;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.totsp.crossword.io.IO;
import com.totsp.crossword.shortyz.R;
import com.totsp.crossword.puz.Playboard.Clue;
import com.totsp.crossword.puz.Puzzle;
import com.totsp.crossword.puz.Note;
import com.totsp.crossword.view.BoardEditText;
import com.totsp.crossword.view.BoardEditText.BoardEditFilter;
import com.totsp.crossword.view.ScrollingImageView;
import com.totsp.crossword.view.ScrollingImageView.ClickListener;
import com.totsp.crossword.puz.Playboard.Word;
import com.totsp.crossword.puz.Playboard.Position;
import com.totsp.crossword.view.ScrollingImageView.Point;
import com.totsp.crossword.puz.Playboard.Word;
import com.totsp.crossword.puz.Box;

public class NotesActivity extends InGameActivity {

    private ScrollingImageView imageView;
    private BoardEditText scratchView;
    private BoardEditText anagramSourceView;
    private BoardEditText anagramSolView;

    private Random rand = new Random();

    private int numAnagramLetters = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (puz == null)
            return;

        utils.holographic(this);
		utils.finishOnHomeButton(this);

        if(BOARD == null || BOARD.getPuzzle() == null){
            finish();
            return;
        }

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

        Note note = puz.getNote(c.number, BOARD.isAcross());
        if (note != null) {
            EditText notesBox = (EditText) this.findViewById(R.id.notesBox);
            notesBox.setText(note.getText());
        }

		scratchView = (BoardEditText) this.findViewById(R.id.scratchMiniboard);
        if (note != null) {
            scratchView.setFromString(note.getSratch());
        }
        scratchView.setLength(curWordLen);
		scratchView.setContextMenuListener(new ClickListener() {
			public void onContextMenu(Point e) {
				copyBoardViewToBoard(scratchView);
			}

			public void onTap(Point e) {
                NotesActivity.this.render();
			}
        });

        anagramSourceView = (BoardEditText) this.findViewById(R.id.anagramSource);
        if (note != null) {
            String src = note.getAnagramSource();
            if (src != null) {
                anagramSourceView.setFromString(src);
                for (int i = 0; i < src.length(); i++) {
                    if (Character.isLetter(src.charAt(i))) {
                        numAnagramLetters++;
                    }
                }
            }
        }

        anagramSolView = (BoardEditText) this.findViewById(R.id.anagramSolution);
        if (note != null) {
            String sol = note.getAnagramSolution();
            if (sol != null) {
                anagramSolView.setFromString(sol);
                for (int i = 0; i < sol.length(); i++) {
                    if (Character.isLetter(sol.charAt(i))) {
                        numAnagramLetters++;
                    }
                }
            }
        }

        BoardEditFilter sourceFilter = new BoardEditFilter() {
            public boolean delete(char oldChar, int pos) {
                if (Character.isLetter(oldChar)) {
                    numAnagramLetters--;
                }
                return true;
            }

            public char filter(char oldChar, char newChar, int pos) {
                if (Character.isLetter(newChar)) {
                    if (Character.isLetter(oldChar)) {
                        return newChar;
                    } else if (numAnagramLetters < curWordLen) {
                        numAnagramLetters++;
                        return newChar;
                    } else {
                        return '\0';
                    }
                } else {
                    return '\0';
                }
            }
        };

        anagramSourceView.setLength(curWordLen);
        anagramSourceView.setFilters(new BoardEditFilter[]{sourceFilter});
		anagramSourceView.setContextMenuListener(new ClickListener() {
			public void onContextMenu(Point e) {
                // reshuffle squares
                int len = anagramSourceView.getLength();
                for (int i = 0; i < len; i++) {
                    int j = rand.nextInt(len);
                    char ci = anagramSourceView.getResponse(i);
                    char cj = anagramSourceView.getResponse(j);
                    anagramSourceView.setResponse(i, cj);
                    anagramSourceView.setResponse(j, ci);
                }
                NotesActivity.this.render();
			}

			public void onTap(Point e) {
                NotesActivity.this.render();
			}
        });

        BoardEditFilter solFilter = new BoardEditFilter() {
            public boolean delete(char oldChar, int pos) {
                if (Character.isLetter(oldChar)) {
                    for (int i = 0; i < curWordLen; i++) {
                        if (anagramSourceView.getResponse(i) == ' ') {
                            anagramSourceView.setResponse(i, oldChar);
                            return true;
                        }
                    }
                }
                return true;
            }

            public char filter(char oldChar, char newChar, int pos) {
                if (Character.isLetter(newChar)) {
                    for (int i = 0; i < curWordLen; i++) {
                        if (anagramSourceView.getResponse(i) == newChar) {
                            anagramSourceView.setResponse(i, oldChar);
                            return newChar;
                        }
                    }
                    // if failed to find it in the source view, see if we can
                    // find one to swap it with one in the solution
                    for (int i = 0; i < curWordLen; i++) {
                        if (anagramSolView.getResponse(i) == newChar) {
                            anagramSolView.setResponse(i, oldChar);
                            return newChar;
                        }
                    }
                }
                return '\0';
            }
        };

        anagramSolView.setLength(curWordLen);
        anagramSolView.setFilters(new BoardEditFilter[]{solFilter});
		anagramSolView.setContextMenuListener(new ClickListener() {
			public void onContextMenu(Point e) {
				copyBoardViewToBoard(anagramSolView);
			}

			public void onTap(Point e) {
                NotesActivity.this.render();
			}
        });


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

        this.render();
    }

    public void onPause() {
        super.onPause();

        EditText notesBox = (EditText) this.findViewById(R.id.notesBox);
        String text = notesBox.getText().toString();

        String scratch = scratchView.toString();
        String anagramSource = anagramSourceView.toString();
        String anagramSolution = anagramSolView.toString();

        Note note = new Note(scratch, text, anagramSource, anagramSolution);

        Clue c = BOARD.getClue();
        puz.setNote(note, c.number, BOARD.isAcross());

		if (this.prefs.getBoolean("forceKeyboard", false)
				|| (this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES)
				|| (this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_UNDEFINED)) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(this.imageView.getWindowToken(), 0);
			imm.hideSoftInputFromWindow(this.scratchView.getWindowToken(), 0);
			imm.hideSoftInputFromWindow(this.anagramSourceView.getWindowToken(), 0);
			imm.hideSoftInputFromWindow(this.anagramSolView.getWindowToken(), 0);
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

        case R.id.anagramSource:
            return anagramSourceView.onKeyUp(keyCode, event);

        case R.id.anagramSolution:
            return anagramSolView.onKeyUp(keyCode, event);

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

            afterPlay();

            return true;
		}

		return super.onKeyUp(keyCode, event);
	}

    private void afterPlay() {
        if (puz.getPercentComplete() == 100) {
            stopTimer();
            Intent i = new Intent(NotesActivity.this, PuzzleFinishedActivity.class);
            this.startActivity(i);
        }
    }

    protected void render() {
        renderKeyboard();
		this.imageView.setBitmap(RENDERER.drawWord());
	}

    private void copyBoardViewToBoard(final BoardEditText view) {
        final Box[] curWordBoxes = BOARD.getCurrentWordBoxes();
        boolean conflicts = false;

        for (int i = 0; i < curWordBoxes.length; i++) {
            char oldResponse = curWordBoxes[i].getResponse();
            if (Character.isLetter(oldResponse) &&
                view.getResponse(i) != oldResponse) {

                conflicts = true;
                break;
            }
        }

        if (conflicts) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setTitle("Copy Conflict");
            builder.setMessage("The new solution conflicts with existing entries.  Overwrite anyway?");
            builder.setPositiveButton("YES",
                                      new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    copyBoardViewToBoardUnchecked(view, curWordBoxes);
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("NO",
                                      new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            AlertDialog alert = builder.create();
            alert.show();
        } else {
            copyBoardViewToBoardUnchecked(view, curWordBoxes);
        }
    }

    private void copyBoardViewToBoardUnchecked(BoardEditText view,
                                               Box[] curWordBoxes) {
        for (int i = 0; i < curWordBoxes.length; i++) {
            curWordBoxes[i].setResponse(view.getResponse(i));
        }

        render();
        afterPlay();
    }
}
