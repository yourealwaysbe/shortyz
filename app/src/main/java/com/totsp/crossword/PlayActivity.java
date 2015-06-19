package com.totsp.crossword;

import static com.totsp.crossword.shortyz.ShortyzApplication.BOARD;
import static com.totsp.crossword.shortyz.ShortyzApplication.RENDERER;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.InflateException;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.totsp.crossword.io.IO;
import com.totsp.crossword.puz.MovementStrategy;
import com.totsp.crossword.puz.Playboard;
import com.totsp.crossword.puz.Playboard.Clue;
import com.totsp.crossword.puz.Playboard.Position;
import com.totsp.crossword.puz.Playboard.Word;
import com.totsp.crossword.puz.Puzzle;
import com.totsp.crossword.shortyz.R;
import com.totsp.crossword.shortyz.ShortyzApplication;
import com.totsp.crossword.view.PlayboardRenderer;
import com.totsp.crossword.view.ScrollingImageView;
import com.totsp.crossword.view.ScrollingImageView.ClickListener;
import com.totsp.crossword.view.ScrollingImageView.Point;
import com.totsp.crossword.view.ScrollingImageView.ScaleListener;
import com.totsp.crossword.view.SeparatedListAdapter;

public class PlayActivity extends InGameActivity {
    private static final Logger LOG = Logger.getLogger("com.totsp.crossword");
    static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    @SuppressWarnings("rawtypes")
    private AdapterView across;
    @SuppressWarnings("rawtypes")
    private AdapterView down;
    private ListView allClues;
    private ClueListAdapter acrossAdapter;
    private ClueListAdapter downAdapter;
    private SeparatedListAdapter allCluesAdapter;
    private MovementStrategy movement = null;
    private ScrollingImageView boardView;
    private TextView clue;
    private boolean fitToScreen;
    private boolean runTimer = false;
    private Runnable updateTimeTask = new Runnable() {
        public void run() {
            if (timer != null) {
                getWindow().setTitle(timer.time());
                getWindow().setFeatureInt(Window.FEATURE_PROGRESS,
                        puz.getPercentComplete() * 100);
            }

            if (runTimer) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    private boolean showCount = false;
    private boolean showErrors = false;
    private long lastKey;
    private long lastTap = 0;
    private long resumedOn;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        this.runTimer = prefs.getBoolean("runTimer", false);

        if (runTimer) {
            this.handler.post(this.updateTimeTask);
        }
    }

    DisplayMetrics metrics;

    /**
     * Called when the activity is first created.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (puz == null)
            return;

        metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        try {
            if (!prefs.getBoolean("showTimer", false)) {
                if (ShortyzApplication.isLandscape(metrics)) {
                    if (ShortyzApplication.isMiniTabletish(metrics)) {
                        utils.hideWindowTitle(this);
                    }
                } else if (android.os.Build.VERSION.SDK_INT < 11) {
                    utils.hideWindowTitle(this);
                }

            } else {
                requestWindowFeature(Window.FEATURE_PROGRESS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        utils.holographic(this);
        utils.finishOnHomeButton(this);

        this.showErrors = this.prefs.getBoolean("showErrors", false);
        setDefaultKeyMode(Activity.DEFAULT_KEYS_DISABLE);

        MovementStrategy movement = this.getMovementStrategy();

        if (prefs.getBoolean("fullScreen", false)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        BOARD = new Playboard(puz, movement);
        RENDERER = new PlayboardRenderer(BOARD, metrics.density,
                !prefs.getBoolean("supressHints", false));

        float scale = prefs.getFloat("scale", metrics.density);

        if (scale > RENDERER.getDeviceMaxScale()) {
            scale = RENDERER.getDeviceMaxScale();
            prefs.edit().putFloat("scale", scale).commit();
        } else if (scale < .5f) {
            scale = .25f;
            prefs.edit().putFloat("scale", .25f).commit();
        } else if (scale == Float.NaN) {
            scale = 1f;
            prefs.edit().putFloat("scale", 1f).commit();
        }

        RENDERER.setScale(scale);
        BOARD.setSkipCompletedLetters(this.prefs.getBoolean("skipFilled",
                false));

        if (puz.getPercentComplete() != 100) {
            this.timer = new ImaginaryTimer(puz.getTime());
            this.timer.start();
            this.runTimer = prefs.getBoolean("showTimer", false);

            if (runTimer) {
                this.handler.post(this.updateTimeTask);
            }
        }

        setContentView(R.layout.play);

        createKeyboard((KeyboardView) this.findViewById(R.id.playKeyboard));

        this.clue = (TextView) this.findViewById(R.id.clueLine);
        if (clue != null && clue.getVisibility() != View.GONE) {
            clue.setVisibility(View.GONE);
            clue = (TextView) utils.onActionBarCustom(this,
                    R.layout.clue_line_only).findViewById(R.id.clueLine);
        }
        this.clue.setClickable(true);
        this.clue.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Intent i = new Intent(PlayActivity.this,
                        ClueListActivity.class);
                i.setData(Uri.fromFile(baseFile));
                PlayActivity.this.startActivityForResult(i, 0);
            }
        });

        boardView = (ScrollingImageView) this.findViewById(R.id.board);
        this.boardView.setCurrentScale(scale);
        this.boardView.setFocusable(true);
        //this.boardView.setOnKeyListener(onKeyListener);
        this.registerForContextMenu(boardView);
        boardView.setContextMenuListener(new ClickListener() {
            public void onContextMenu(final Point e) {
                handler.post(new Runnable() {
                    public void run() {
                        try {
                            Position p = RENDERER.findBox(e);
                            Word w = BOARD.setHighlightLetter(p);
                            RENDERER.draw(w);
                            PlayActivity.this.openContextMenu(boardView);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                });
            }

            public void onTap(Point e) {
                try {
                    if (prefs.getBoolean("doubleTap", false)
                            && ((System.currentTimeMillis() - lastTap) < 300)) {
                        if (fitToScreen) {
                            RENDERER.setScale(prefs.getFloat("scale", 1F));
                            boardView.setCurrentScale(1F);
                            BOARD.setHighlightLetter(RENDERER.findBox(e));
                            render();
                        } else {
                            int w = boardView.getWidth();
                            int h = boardView.getHeight();
                            float scale = RENDERER.fitTo((w < h) ? w : h);
                            boardView.setCurrentScale(scale);
                            render(true);
                            boardView.scrollTo(0, 0);
                        }

                        fitToScreen = !fitToScreen;
                    } else {
                        Position p = RENDERER.findBox(e);
                        Word old = BOARD.setHighlightLetter(p);
                        PlayActivity.this.render(old);
                    }

                    lastTap = System.currentTimeMillis();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        if (puz.getPercentComplete() != 100) {
            this.runTimer = prefs.getBoolean("showTimer", false);
            if (runTimer) {
                this.handler.post(this.updateTimeTask);
            }
        }


        this.boardView.setFocusable(true);
        this.boardView.setScaleListener(new ScaleListener() {
            TimerTask t;
            Timer renderTimer = new Timer();

            public void onScale(float newScale, final Point center) {
                //fitToScreen = false;

                if (t != null) {
                    t.cancel();
                }

                renderTimer.purge();
                t = new TimerTask() {
                    @Override
                    public void run() {
                        handler.post(new Runnable() {
                            public void run() {
                                int w = boardView.getImageView().getWidth();
                                int h = boardView.getImageView().getHeight();
                                float scale = RENDERER.fitTo((w < h) ? w : h);
                                prefs.edit()
                                        .putFloat("scale",
                                                scale)
                                        .commit();
                                BOARD.setHighlightLetter(RENDERER
                                        .findBox(center));

                                render(true);
                            }
                        });
                    }
                };
                renderTimer.schedule(t, 500);
                lastTap = System.currentTimeMillis();
            }
        });

        if (puz.isUpdatable()) {
            this.showErrors = false;
        }

        if (BOARD.isShowErrors() != this.showErrors) {
            BOARD.toggleShowErrors();
        }


        this.render(true);

        this.across = (AdapterView) this.findViewById(R.id.acrossList);
        this.down = (AdapterView) this.findViewById(R.id.downList);
        boolean isGal = false;
        if ((this.across == null) && (this.down == null)) {
            this.across = (AdapterView) this.findViewById(R.id.acrossListGal);
            this.down = (AdapterView) this.findViewById(R.id.downListGal);
            isGal = true;
        }

        if ((across != null) && (down != null)) {
            across.setAdapter(this.acrossAdapter = new ClueListAdapter(this,
                    BOARD.getAcrossClues(), true));
            down.setAdapter(this.downAdapter = new ClueListAdapter(this, BOARD
                    .getDownClues(), false));
            if (!isGal) {
                across.setOnItemClickListener(new OnItemClickListener() {
                    public void onItemClick(AdapterView<?> arg0, View arg1,
                                            int arg2, long arg3) {
                        arg0.setSelected(true);
                        BOARD.jumpTo(arg2, true);
                        render();
                    }
                });
            }

            across.setOnItemSelectedListener(new OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int arg2, long arg3) {
                    if (!BOARD.isAcross()
                            || (BOARD.getCurrentClueIndex() != arg2)) {
                        BOARD.jumpTo(arg2, true);
                        render();
                    }
                }

                public void onNothingSelected(AdapterView<?> view) {
                }
            });

            if (!isGal) {
                down.setOnItemClickListener(new OnItemClickListener() {
                    public void onItemClick(AdapterView<?> arg0, View arg1,
                                            final int arg2, long arg3) {
                        BOARD.jumpTo(arg2, false);
                        render();
                    }
                });
            }

            down.setOnItemSelectedListener(new OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int arg2, long arg3) {
                    if (BOARD.isAcross()
                            || (BOARD.getCurrentClueIndex() != arg2)) {
                        BOARD.jumpTo(arg2, false);
                        render();
                    }
                }

                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });
            down.scrollTo(0, 0);
            across.scrollTo(0, 0);

            View.OnKeyListener onKeyListener = new View.OnKeyListener() {
                @Override
                public boolean onKey(View view, int i, KeyEvent keyEvent) {
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        return PlayActivity.this.onKeyUp(i, keyEvent);
                    } else {
                        return false;
                    }
                }
            };

            down.setOnKeyListener(onKeyListener);
            across.setOnKeyListener(onKeyListener);
            down.setFocusable(false);
            across.setFocusable(false);
        }
        this.allClues = (ListView) this.findViewById(R.id.allClues);
        if (this.allClues != null) {
            this.allCluesAdapter = new SeparatedListAdapter(this);
            this.allCluesAdapter.addSection(
                    "Across",
                    this.acrossAdapter = new ClueListAdapter(this, BOARD
                            .getAcrossClues(), true));
            this.allCluesAdapter.addSection(
                    "Down",
                    this.downAdapter = new ClueListAdapter(this, BOARD
                            .getDownClues(), false));
            allClues.setAdapter(this.allCluesAdapter);

            allClues.setOnItemClickListener(new OnItemClickListener() {
                public void onItemClick(AdapterView<?> arg0, View arg1,
                                        int clickIndex, long arg3) {
                    boolean across = clickIndex <= BOARD.getAcrossClues().length + 1;
                    int index = clickIndex - 1;
                    if (index > BOARD.getAcrossClues().length) {
                        index = index - BOARD.getAcrossClues().length - 1;
                    }
                    arg0.setSelected(true);
                    BOARD.jumpTo(index, across);
                    render();
                }
            });
            allClues.setOnItemSelectedListener(new OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int clickIndex, long arg3) {
                    boolean across = clickIndex <= BOARD.getAcrossClues().length + 1;
                    int index = clickIndex - 1;
                    if (index > BOARD.getAcrossClues().length) {
                        index = index - BOARD.getAcrossClues().length - 1;
                    }
                    if (!BOARD.isAcross() == across && BOARD.getCurrentClueIndex() != index) {
                        arg0.setSelected(true);
                        BOARD.jumpTo(index, across);
                        render();
                    }
                }

                public void onNothingSelected(AdapterView<?> view) {
                }
            });
        }
        if (!prefs.getBoolean("showTimer", false)) {
            if (ShortyzApplication.isLandscape(metrics)) {
                if (ShortyzApplication.isMiniTabletish(metrics) && allClues != null) {
                    utils.hideActionBar(this);
                }
            }
        }

        this.setClueSize(prefs.getInt("clueSize", 12));
        setTitle("Shortyz - " + puz.getTitle() + " - " + puz.getAuthor()
                + " -	" + puz.getCopyright());
        this.showCount = prefs.getBoolean("showCount", false);
        if (this.prefs.getBoolean("fitToScreen", false) || (android.os.Build.VERSION.SDK_INT > 11 && ShortyzApplication.isLandscape(metrics)) && (ShortyzApplication.isTabletish(metrics) || ShortyzApplication.isMiniTabletish(metrics))) {
            this.handler.postDelayed(new Runnable() {

                public void run() {
                    boardView.scrollTo(0, 0);

                    int v = (boardView.getWidth() < boardView.getHeight()) ? boardView
                            .getWidth() : boardView.getHeight();
                    if (v == 0) {
                        handler.postDelayed(this, 100);
                    }
                    float newScale = RENDERER.fitTo(v);
                    boardView.setCurrentScale(newScale);

                    prefs.edit().putFloat("scale", newScale).commit();
                    render();
                }

            }, 100);

        }

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
                                    ContextMenuInfo info) {
        // System.out.println("CCM " + view);
//        if (view == boardView) {
//            Menu clueSize = menu.addSubMenu("Clue Text Size");
//            clueSize.add("Small");
//            clueSize.add("Medium");
//            clueSize.add("Large");
//
//            menu.add("Zoom In");
//
//            if (RENDERER.getScale() < RENDERER.getDeviceMaxScale())
//                menu.add("Zoom In Max");
//
//            menu.add("Zoom Out");
//            menu.add("Fit to Screen");
//            menu.add("Zoom Reset");
//        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("Clues").setIcon(android.R.drawable.ic_menu_agenda);
        menu.add("Notes").setIcon(android.R.drawable.ic_menu_agenda);
        Menu clueSize = menu.addSubMenu("Clue Text Size");
        clueSize.add(createSpannableForMenu("Small")).setTitleCondensed("Small");
        clueSize.add(createSpannableForMenu("Medium")).setTitleCondensed("Medium");
        clueSize.add(createSpannableForMenu("Large")).setTitleCondensed("Large");
        Menu zoom = menu.addSubMenu("Zoom");
        zoom.add(createSpannableForMenu("Zoom In")).setTitleCondensed("Zoom In");

        if (RENDERER.getScale() < RENDERER.getDeviceMaxScale())
            zoom.add(createSpannableForMenu("Zoom In Max")).setTitleCondensed("Zoom In Max");

        zoom.add(createSpannableForMenu("Zoom Out")).setTitleCondensed("Zoom Out");
        zoom.add(createSpannableForMenu("Fit to Screen")).setTitleCondensed("Fit to Screen");
        zoom.add(createSpannableForMenu("Zoom Reset")).setTitleCondensed("Zoom Reset");

        return super.onCreateOptionsMenu(menu);
    }

    private SpannableString createSpannableForMenu(String value){
        SpannableString s = new SpannableString(value);
        s.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.textColorPrimary)), 0, s.length(), 0);
        return s;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true;
        }

        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Word previous;

        if ((System.currentTimeMillis() - this.resumedOn) < 500) {
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                System.out.println("Next clue.");
                BOARD.setMovementStrategy(MovementStrategy.MOVE_NEXT_CLUE);
                previous = BOARD.nextWord();
                BOARD.setMovementStrategy(this.getMovementStrategy());
                this.render(previous);

                return true;

            case KeyEvent.KEYCODE_BACK:
                this.finish();

                return true;

            case KeyEvent.KEYCODE_MENU:
                return false;

            case KeyEvent.KEYCODE_DPAD_DOWN:

                if ((System.currentTimeMillis() - lastKey) > 50) {
                    previous = BOARD.moveDown();
                    this.render(previous);
                }


                lastKey = System.currentTimeMillis();

                return true;
            case KeyEvent.KEYCODE_DPAD_UP:

                if ((System.currentTimeMillis() - lastKey) > 50) {
                    previous = BOARD.moveUp();
                    this.render(previous);
                }

                lastKey = System.currentTimeMillis();

                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:

                if ((System.currentTimeMillis() - lastKey) > 50) {
                    previous = BOARD.moveLeft();
                    this.render(previous);
                }

                lastKey = System.currentTimeMillis();

                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:

                if ((System.currentTimeMillis() - lastKey) > 50) {
                    previous = BOARD.moveRight();
                    this.render(previous);
                }

                lastKey = System.currentTimeMillis();

                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
                previous = BOARD.toggleDirection();
                this.render(previous);

                return true;

            case KeyEvent.KEYCODE_SPACE:

                if ((System.currentTimeMillis() - lastKey) > 150) {
                    if (prefs.getBoolean("spaceChangesDirection", true)) {
                        previous = BOARD.toggleDirection();
                        this.render(previous);
                    } else {
                        previous = BOARD.playLetter(' ');
                        this.render(previous);
                    }
                }

                lastKey = System.currentTimeMillis();

                return true;

            case KeyEvent.KEYCODE_ENTER:

                if ((System.currentTimeMillis() - lastKey) > 150) {
                    if (prefs.getBoolean("enterChangesDirection", true)) {
                        previous = BOARD.toggleDirection();
                        this.render(previous);
                    } else {
                        previous = BOARD.nextWord();
                        this.render(previous);
                    }

                    lastKey = System.currentTimeMillis();

                    return true;
                }

            case KeyEvent.KEYCODE_DEL:

                if ((System.currentTimeMillis() - lastKey) > 150) {
                    previous = BOARD.deleteLetter();
                    this.render(previous);
                }

                lastKey = System.currentTimeMillis();

                return true;
        }

        char c = Character
                .toUpperCase(((this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) || this.useNativeKeyboard) ? event
                        .getDisplayLabel() : ((char) keyCode));

        if (ALPHA.indexOf(c) != -1) {
            previous = BOARD.playLetter(c);
            this.render(previous);

            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println(item.getTitle());
        if (item.getTitle() == null) {
            finish();
            return true;
        } else if (item.getTitle().toString().equals("Zoom In")) {
            this.boardView.scrollTo(0, 0);

            float newScale = RENDERER.zoomIn();
            this.prefs.edit().putFloat("scale", newScale).commit();
            this.fitToScreen = false;
            boardView.setCurrentScale(newScale);
            this.render();

            return true;
        } else if (item.getTitle().toString().equals("Zoom In Max")) {
            this.boardView.scrollTo(0, 0);

            float newScale = RENDERER.zoomInMax();
            this.prefs.edit().putFloat("scale", newScale).commit();
            this.fitToScreen = false;
            boardView.setCurrentScale(newScale);
            this.render();

            return true;
        } else if (item.getTitle().toString().equals("Zoom Out")) {
            this.boardView.scrollTo(0, 0);

            float newScale = RENDERER.zoomOut();
            this.prefs.edit().putFloat("scale", newScale).commit();
            this.fitToScreen = false;
            boardView.setCurrentScale(newScale);
            this.render();

            return true;
        } else if (item.getTitle().toString().equals("Fit to Screen")) {
            this.boardView.scrollTo(0, 0);

            int v = (this.boardView.getWidth() < this.boardView.getHeight()) ? this.boardView
                    .getWidth() : this.boardView.getHeight();
            float newScale = RENDERER.fitTo(v);
            this.prefs.edit().putFloat("scale", newScale).commit();
            boardView.setCurrentScale(newScale);
            this.render();

            return true;
        } else if (item.getTitle().toString().equals("Zoom Reset")) {
            float newScale = RENDERER.zoomReset();
            boardView.setCurrentScale(newScale);
            this.prefs.edit().putFloat("scale", newScale).commit();
            this.render();
            this.boardView.scrollTo(0, 0);

            return true;
        } else if (item.getTitle().toString().equals("Clues")) {
            Intent i = new Intent(PlayActivity.this, ClueListActivity.class);
            i.setData(Uri.fromFile(baseFile));
            PlayActivity.this.startActivityForResult(i, 0);

            return true;
        } else if (item.getTitle().toString().equals("Notes")) {
            Intent i = new Intent(PlayActivity.this, NotesActivity.class);
            i.setData(Uri.fromFile(baseFile));
            PlayActivity.this.startActivityForResult(i, 0);

            return true;
        } else if (item.getTitle().toString().equals("Small")) {
            this.setClueSize(12);
        } else if (item.getTitle().toString().equals("Medium")) {
            this.setClueSize(14);
        } else if (item.getTitle().toString().equals("Large")) {
            this.setClueSize(16);
        }

        return super.onOptionsItemSelected(item);
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        this.render();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if ((this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES)
                || (this.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_UNDEFINED)) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(clue.getWindowToken(), 0);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.resumedOn = System.currentTimeMillis();
        BOARD.setSkipCompletedLetters(this.prefs
                .getBoolean("skipFilled", false));
        BOARD.setMovementStrategy(this.getMovementStrategy());

        onResumeKeyboard((KeyboardView) this.findViewById(R.id.playKeyboard));

        this.showCount = prefs.getBoolean("showCount", false);
        this.onConfigurationChanged(this.configuration);

        this.runTimer = prefs.getBoolean("showTimer", false);

        if (runTimer) {
            this.handler.post(this.updateTimeTask);
        }

        render();
    }

    private void setClueSize(int dps) {
        this.clue.setTextSize(TypedValue.COMPLEX_UNIT_SP, dps);

        if ((acrossAdapter != null) && (downAdapter != null)) {
            acrossAdapter.textSize = dps;
            acrossAdapter.notifyDataSetInvalidated();
            downAdapter.textSize = dps;
            downAdapter.notifyDataSetInvalidated();
        }

        if (prefs.getInt("clueSize", 12) != dps) {
            this.prefs.edit().putInt("clueSize", dps).commit();
        }
    }

    private MovementStrategy getMovementStrategy() {
        if (movement != null) {
            return movement;
        } else {
            String stratName = this.prefs.getString("movementStrategy",
                    "MOVE_NEXT_ON_AXIS");
            if (stratName.equals("MOVE_NEXT_ON_AXIS")) {
                movement = MovementStrategy.MOVE_NEXT_ON_AXIS;
            } else if (stratName.equals("STOP_ON_END")) {
                movement = MovementStrategy.STOP_ON_END;
            } else if (stratName.equals("MOVE_NEXT_CLUE")) {
                movement = MovementStrategy.MOVE_NEXT_CLUE;
            } else if (stratName.equals("MOVE_PARALLEL_WORD")) {
                movement = MovementStrategy.MOVE_PARALLEL_WORD;
            }

            return movement;
        }
    }

    protected void render() {
        render(null);
    }

    private void render(boolean rescale) {
        this.render(null, rescale);
    }

    private void render(Word previous) {
        this.render(previous, true);
    }

    private void render(Word previous, boolean rescale) {
        renderKeyboard();

        Clue c = BOARD.getClue();
        BOARD.toggleDirection();
        BOARD.toggleDirection();

        if (c.hint == null) {
            BOARD.toggleDirection();
            c = BOARD.getClue();
        }

        this.boardView.setBitmap(RENDERER.draw(previous), rescale);
        this.boardView.requestFocus();
        /*
		 * If we jumped to a new word, ensure the first letter is visible.
		 * Otherwise, insure that the current letter is visible. Only necessary
		 * if the cursor is currently off screen.
		 */
        if (rescale && this.prefs.getBoolean("ensureVisible", true)) {
            Point topLeft;
            Point bottomRight;
            Point cursorTopLeft;
            Point cursorBottomRight;
            cursorTopLeft = RENDERER.findPointTopLeft(BOARD
                    .getHighlightLetter());
            cursorBottomRight = RENDERER.findPointBottomRight(BOARD
                    .getHighlightLetter());

            if ((previous != null) && previous.equals(BOARD.getCurrentWord())) {
                topLeft = cursorTopLeft;
                bottomRight = cursorBottomRight;
            } else {
                topLeft = RENDERER
                        .findPointTopLeft(BOARD.getCurrentWordStart());
                bottomRight = RENDERER.findPointBottomRight(BOARD
                        .getCurrentWordStart());
            }

            int tlDistance = cursorTopLeft.distance(topLeft);
            int brDistance = cursorBottomRight.distance(bottomRight);

            if (!this.boardView.isVisible(topLeft) && (tlDistance < brDistance)) {
                this.boardView.ensureVisible(topLeft);
            }

            if (!this.boardView.isVisible(bottomRight)
                    && (brDistance < tlDistance)) {
                this.boardView.ensureVisible(bottomRight);
            }

            // ensure the cursor is always on the screen.
            this.boardView.ensureVisible(cursorBottomRight);
            this.boardView.ensureVisible(cursorTopLeft);

        }

        this.clue
                .setText("("
                        + (BOARD.isAcross() ? "across" : "down")
                        + ") "
                        + c.number
                        + ". "
                        + c.hint
                        + (this.showCount ? ("  ["
                        + BOARD.getCurrentWord().length + "]") : ""));

        if (this.allClues != null) {
            if (BOARD.isAcross()) {
                ClueListAdapter cla = (ClueListAdapter) this.allCluesAdapter.sections
                        .get(0);
                cla.setActiveDirection(BOARD.isAcross());
                cla.setHighlightClue(c);
                this.allCluesAdapter.notifyDataSetChanged();
                this.allClues.setSelectionFromTop(cla.indexOf(c) + 1,
                        (this.allClues.getHeight() / 2) - 50);
            } else {
                ClueListAdapter cla = (ClueListAdapter) this.allCluesAdapter.sections
                        .get(1);
                cla.setActiveDirection(!BOARD.isAcross());
                cla.setHighlightClue(c);
                this.allCluesAdapter.notifyDataSetChanged();
                this.allClues.setSelectionFromTop(
                        cla.indexOf(c) + BOARD.getAcrossClues().length + 2,
                        (this.allClues.getHeight() / 2) - 50);
            }
        }

        if (this.down != null) {
            this.downAdapter.setHighlightClue(c);
            this.downAdapter.setActiveDirection(!BOARD.isAcross());
            this.downAdapter.notifyDataSetChanged();

            if (!BOARD.isAcross() && !c.equals(this.down.getSelectedItem())) {
                if (this.down instanceof ListView) {
                    ((ListView) this.down).setSelectionFromTop(
                            this.downAdapter.indexOf(c),
                            (down.getHeight() / 2) - 50);
                } else {
                    // Gallery
                    this.down.setSelection(this.downAdapter.indexOf(c));
                }
            }
        }

        if (this.across != null) {
            this.acrossAdapter.setHighlightClue(c);
            this.acrossAdapter.setActiveDirection(BOARD.isAcross());
            this.acrossAdapter.notifyDataSetChanged();

            if (BOARD.isAcross() && !c.equals(this.across.getSelectedItem())) {
                if (across instanceof ListView) {
                    ((ListView) this.across).setSelectionFromTop(
                            this.acrossAdapter.indexOf(c),
                            (across.getHeight() / 2) - 50);
                } else {
                    // Gallery view
                    this.across.setSelection(this.acrossAdapter.indexOf(c));
                }
            }
        }

        if ((puz.getPercentComplete() == 100)) {
            stopTimer();
            Intent i = new Intent(PlayActivity.this,
                    PuzzleFinishedActivity.class);
            this.startActivity(i);

        }
        this.boardView.requestFocus();
    }


}
