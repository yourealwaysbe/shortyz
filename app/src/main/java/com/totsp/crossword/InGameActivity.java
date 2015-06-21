
package com.totsp.crossword;

import static com.totsp.crossword.shortyz.ShortyzApplication.BOARD;

import java.io.File;
import java.io.IOException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.totsp.crossword.io.IO;
import com.totsp.crossword.puz.Puzzle;
import com.totsp.crossword.shortyz.R;
import com.totsp.crossword.shortyz.ShortyzApplication;

public abstract class InGameActivity extends ShortyzActivity {
    private static final int INFO_DIALOG = 0;
    private static final int REVEAL_PUZZLE_DIALOG = 2;

    private Dialog dialog;
    private AlertDialog revealPuzzleDialog;

    protected Puzzle puz;
    protected File baseFile;

    protected ImaginaryTimer timer;

    /**
     * If puz is null after this call, do not continue
     * uses the puzzle on BOARD unless BOARD is null
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            if (BOARD != null)
                puz = BOARD.getPuzzle();

            Uri u = this.getIntent().getData();

            baseFile = null;
            if (u != null) {
                if (u.getScheme().equals("file")) {
                    baseFile = new File(u.getPath());
                }
            }

            if (puz == null) {
                puz = IO.load(baseFile);

                if (puz == null) {
                    throw new IOException();
                }
            }

            revealPuzzleDialog = new AlertDialog.Builder(this).create();
            revealPuzzleDialog.setTitle("Reveal Entire Puzzle");
            revealPuzzleDialog.setMessage("Are you sure?");

            revealPuzzleDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            BOARD.revealPuzzle();
                            render();
                        }
                    });
            revealPuzzleDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

            if (puz.getPercentComplete() != 100) {
                this.timer = new ImaginaryTimer(puz.getTime());
                this.timer.start();
            }

        } catch (IOException e) {
            System.err.println(this.getIntent().getData());
            e.printStackTrace();

            String filename = null;

            try {
                filename = this.baseFile.getName();
            } catch (Exception ee) {
                e.printStackTrace();
            }

            Toast t = Toast.makeText(this,
                                     (("Unable to read file" + filename) != null)
                                         ? (" \n" + filename)
                                         : "", Toast.LENGTH_SHORT);
            t.show();
            this.finish();
        }
    }

    @Override
    protected void onPause() {
        stopTimer();

        try {
			if ((puz != null) && (baseFile != null)) {
				IO.save(puz, baseFile);
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

        super.onPause();
    }

    @Override
    protected void onRestart() {
        if (puz.getPercentComplete() != 100 && this.timer == null) {
            this.timer = new ImaginaryTimer(puz.getTime());
            this.timer.start();
        }

        super.onRestart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!puz.isUpdatable()) {
            boolean showErrors = this.prefs.getBoolean("showErrors", false);
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

            MenuItem showItem = menu.add(
                    showErrors ? "Hide Errors" : "Show Errors").setIcon(
                    android.R.drawable.ic_menu_view);
            if (ShortyzApplication.isTabletish(metrics)) {
                utils.onActionBarWithText(showItem);
            }

            SubMenu reveal = menu.addSubMenu("Reveal").setIcon(
                    android.R.drawable.ic_menu_view);
            reveal.add(createSpannableForMenu("Letter")).setTitleCondensed("Letter");
            reveal.add(createSpannableForMenu("Word")).setTitleCondensed("Word");
            reveal.add(createSpannableForMenu("Puzzle")).setTitleCondensed("Puzzle");
            if (ShortyzApplication.isTabletish(metrics)) {
                utils.onActionBarWithText(reveal);
            }
        } else {
            menu.add("Show Errors").setEnabled(false)
                    .setIcon(android.R.drawable.ic_menu_view);
            menu.add("Reveal").setIcon(android.R.drawable.ic_menu_view)
                    .setEnabled(false);
        }

        menu.add("Info").setIcon(android.R.drawable.ic_menu_info_details);

        return super.onCreateOptionsMenu(menu);
    }

    private SpannableString createSpannableForMenu(String value){
        SpannableString s = new SpannableString(value);
        s.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.textColorPrimary)), 0, s.length(), 0);
        return s;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().toString().equals("Letter")) {
            BOARD.revealLetter();
            this.render();

            return true;
        } else if (item.getTitle().toString().equals("Word")) {
            BOARD.revealWord();
            this.render();

            return true;
        } else if (item.getTitle().toString().equals("Puzzle")) {
            this.showDialog(REVEAL_PUZZLE_DIALOG);

            return true;
        } else if (item.getTitle().toString().equals("Show Errors")
                || item.getTitle().toString().equals("Hide Errors")) {
            BOARD.toggleShowErrors();
            item.setTitle(BOARD.isShowErrors() ? "Hide Errors" : "Show Errors");
            this.prefs.edit().putBoolean("showErrors", BOARD.isShowErrors())
                    .commit();
            this.render();

            return true;
        } else if (item.getTitle().toString().equals("Info")) {
            if (dialog != null) {
                TextView view = (TextView) dialog
                        .findViewById(R.id.puzzle_info_time);

                if (timer != null) {
                    this.timer.stop();
                    view.setText("Elapsed Time: " + this.timer.time());
                    this.timer.start();
                } else {
                    view.setText("Elapsed Time: "
                            + new ImaginaryTimer(puz.getTime()).time());
                }
            }

            this.showDialog(INFO_DIALOG);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case INFO_DIALOG:
                // This is weird. I don't know why a rotate resets the dialog.
                // Whatevs.
                return createInfoDialog();

            case REVEAL_PUZZLE_DIALOG:
                return revealPuzzleDialog;

            default:
                return null;
        }
    }

    private Dialog createInfoDialog() {
        if (dialog == null) {
            dialog = new Dialog(this);
        }

        dialog.setTitle("Puzzle Info");
        dialog.setContentView(R.layout.puzzle_info_dialog);

        TextView view = (TextView) dialog.findViewById(R.id.puzzle_info_title);
        view.setText(this.puz.getTitle());
        view = (TextView) dialog.findViewById(R.id.puzzle_info_author);
        view.setText(this.puz.getAuthor());
        view = (TextView) dialog.findViewById(R.id.puzzle_info_copyright);
        view.setText(this.puz.getCopyright());
        view = (TextView) dialog.findViewById(R.id.puzzle_info_time);

        if (this.timer != null) {
            this.timer.stop();
            view.setText("Elapsed Time: " + this.timer.time());
            this.timer.start();
        } else {
            view.setText("Elapsed Time: "
                    + new ImaginaryTimer(puz.getTime()).time());
        }

        ProgressBar progress = (ProgressBar) dialog
                .findViewById(R.id.puzzle_info_progress);
        progress.setProgress(this.puz.getPercentComplete());

        return dialog;
    }

    protected void stopTimer() {
        if (timer != null) {
            timer.stop();
            puz.setTime(timer.getElapsed());
        }
        this.timer = null;
    }

    // This is used as a proxy for "do we already know the puzzle has been
    // completed"
    protected boolean isTiming() {
        return timer != null;
    }

    protected abstract void render();
}

