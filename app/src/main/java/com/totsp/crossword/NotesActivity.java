package com.totsp.crossword;

import static com.totsp.crossword.shortyz.ShortyzApplication.BOARD;
import static com.totsp.crossword.shortyz.ShortyzApplication.RENDERER;

import android.os.Bundle;
import android.net.Uri;
import android.widget.EditText;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.View;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.InputType;

import com.totsp.crossword.shortyz.R;
import com.totsp.crossword.puz.Playboard.Clue;
import com.totsp.crossword.puz.Puzzle;
import com.totsp.crossword.puz.Note;
import com.totsp.crossword.view.ScrollingImageView;

public class NotesActivity extends ShortyzActivity {

    private final int NO_PREDICT_INPUT
        = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
          InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.notes);

        Clue c = BOARD.getClue();

        boolean showCount = prefs.getBoolean("showCount", false);

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
                     + BOARD.getCurrentWord().length + "]") : ""));

		ScrollingImageView imageView
            = (ScrollingImageView) this.findViewById(R.id.miniboard);
		imageView.setBitmap(RENDERER.drawWord());

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
    }


    private static final boolean isAnagramSolutionSpecialChar(char c) {
        switch (c) {
        case ' ':
        case '-':
            return true;
        default:
            return false;
        }
    }
}
