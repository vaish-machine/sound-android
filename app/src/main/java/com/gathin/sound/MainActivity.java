package com.gathin.sound;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_PICK_FOLDER = 1001;

    private static final int BG = Color.rgb(12, 16, 27);
    private static final int PANEL = Color.rgb(25, 33, 49);
    private static final int ACCENT = Color.rgb(111, 177, 255);
    private static final int PURPLE = Color.rgb(181, 145, 255);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(158, 171, 193);

    private final List<Song> songs = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ExecutorService scanExecutor =
        Executors.newSingleThreadExecutor();

    private ExoPlayer player;

    private LinearLayout songContainer;

    private TextView folderButton;
    private TextView folderLabel;
    private TextView libraryStatus;
    private TextView currentTitle;
    private TextView playPauseButton;
    private TextView currentTime;
    private TextView totalTime;

    private SeekBar seekBar;

    private boolean userSeeking = false;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private static class Song {
        final String name;
        final Uri uri;

        Song(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    private int dp(float value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private TextView makeText(
        String value, float size, int color, boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView makeButton(String value) {
        TextView view = makeText(value, 15, WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setBackground(rounded(PANEL, 15));
        return view;
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(
            1, dp(height)
        ));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        buildInterface();
        initializePlayer();

        SharedPreferences preferences =
            getSharedPreferences("sound_library", MODE_PRIVATE);

        String savedFolder = preferences.getString("music_folder", null);

        if (savedFolder != null) {
            loadFolder(Uri.parse(savedFolder));
        } else {
            libraryStatus.setText(
                "Choose your music folder to get started."
            );

            handler.postDelayed(() -> {
                if (!isFinishing()) {
                    openFolderPicker();
                }
            }, 400);
        }

        handler.post(progressUpdater);
    }

    private void buildInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(22), dp(24), dp(22), dp(12));

        TextView brand = makeText("S O U N D", 14, ACCENT, true);
        brand.setLetterSpacing(0.18f);

        root.addView(brand, new LinearLayout.LayoutParams(
            -1, dp(28)
        ));

        TextView heading = makeText(
            "Your music.\nYour way.",
            31,
            WHITE,
            true
        );
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setLineSpacing(dp(4), 1f);

        LinearLayout.LayoutParams headingParams =
            new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(12);

        root.addView(heading, headingParams);

        TextView subtitle = makeText(
            "Every sound deserves a home.",
            14,
            MUTED,
            false
        );

        LinearLayout.LayoutParams subtitleParams =
            new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(8);

        root.addView(subtitle, subtitleParams);

        folderButton = makeButton("＋   CHOOSE MUSIC FOLDER");
        folderButton.setBackground(rounded(
            Color.rgb(42, 77, 124), 15
        ));
        folderButton.setOnClickListener(v -> openFolderPicker());

        LinearLayout.LayoutParams folderParams =
            new LinearLayout.LayoutParams(-1, dp(52));
        folderParams.topMargin = dp(25);

        root.addView(folderButton, folderParams);

        folderLabel = makeText(
            "No folder selected", 12, MUTED, false
        );

        LinearLayout.LayoutParams folderLabelParams =
            new LinearLayout.LayoutParams(-1, -2);
        folderLabelParams.topMargin = dp(12);

        root.addView(folderLabel, folderLabelParams);

        libraryStatus = makeText(
            "Your music library will appear here.",
            13,
            ACCENT,
            false
        );

        LinearLayout.LayoutParams statusParams =
            new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(15);
        statusParams.bottomMargin = dp(12);

        root.addView(libraryStatus, statusParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);

        songContainer = new LinearLayout(this);
        songContainer.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(songContainer);

        root.addView(scrollView,
            new LinearLayout.LayoutParams( -1, 0, 1f ));

        LinearLayout playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setPadding(
            dp(16), dp(15), dp(16), dp(12)
        );
        playerCard.setBackground(rounded(PANEL, 20));

        LinearLayout.LayoutParams cardParams =
            new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(12);
        cardParams.bottomMargin = dp(4);

        root.addView(playerCard, cardParams);

        TextView nowPlaying = makeText(
            "NOW PLAYING", 10, ACCENT, true
        );
        nowPlaying.setLetterSpacing(0.12f);

        playerCard.addView(nowPlaying);

        currentTitle = makeText(
            "Nothing playing", 16, WHITE, true
        );
        currentTitle.setSingleLine(true);
        currentTitle.setEllipsize(TextUtils.TruncateAt.END);

        LinearLayout.LayoutParams trackParams =
            new LinearLayout.LayoutParams(-1, -2);
        trackParams.topMargin = dp(7);

        playerCard.addView(currentTitle, trackParams);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgressTintList(
            ColorStateList.valueOf(ACCENT)
        );
        seekBar.setThumbTintList(
            ColorStateList.valueOf(PURPLE)
        );
        seekBar.setProgressBackgroundTintList(
            ColorStateList.valueOf(Color.rgb(65, 76, 95))
        );

        playerCard.addView(seekBar,
            new LinearLayout.LayoutParams(-1, dp(36)));

        seekBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser
                ) {
                    if (fromUser && player != null) {
                        long duration = player.getDuration();

                        if (duration > 0 && duration != C.TIME_UNSET) {
                            currentTime.setText(formatTime(
                                duration * progress / 1000
                            ));
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                    userSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    if (player != null) {
                        long duration = player.getDuration();

                        if (duration > 0 && duration != C.TIME_UNSET) {
                            player.seekTo(
                                duration * bar.getProgress() / 1000
                            );
                        }
                    }

                    userSeeking = false;
                    updateProgress();
                }
            }
        );

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);

        currentTime = makeText("0:00", 11, MUTED, false);
        totalTime = makeText("0:00", 11, MUTED, false);
        totalTime.setGravity(Gravity.END);

        times.addView(currentTime,
            new LinearLayout.LayoutParams(0, -2, 1f));
        times.addView(totalTime,
            new LinearLayout.LayoutParams(0, -2, 1f));

        playerCard.addView(times);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        TextView previous = makeButton("|◀");
        TextView next = makeButton("▶|");

        playPauseButton = makeButton("▶");
        playPauseButton.setTextSize(22);
        playPauseButton.setTextColor(BG);
        playPauseButton.setBackground(rounded(ACCENT, 40));

        LinearLayout.LayoutParams controlParams =
            new LinearLayout.LayoutParams(dp(55), dp(48));
        controlParams.setMargins(dp(13), dp(8), dp(13), dp(2));

        controls.addView(previous, new LinearLayout.LayoutParams(
            dp(52), dp(45)
        ));

        controls.addView(playPauseButton, controlParams);

        controls.addView(next, new LinearLayout.LayoutParams(
            dp(52), dp(45)
        ));

        previous.setOnClickListener(v -> previousTrack());
        next.setOnClickListener(v -> nextTrack());
        playPauseButton.setOnClickListener(v -> togglePlayback());

        playerCard.addView(controls);

        setContentView(root);
    }

    private void initializePlayer() {

        player = new ExoPlayer.Builder(this).build();

        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build();

        player.setAudioAttributes(attributes, true);

        player.addListener(new Player.Listener() {

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayButton();
                updateProgress();
            }

            @Override
            public void onMediaItemTransition(
                MediaItem item, int reason
            ) {
                int index = player.getCurrentMediaItemIndex();

                if (index >= 0 && index < songs.size()) {
                    currentTitle.setText(songs.get(index).name);
                }

                refreshSongRows();
                updateProgress();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                updateProgress();
                updatePlayButton();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(
                    MainActivity.this,
                    "Unable to play this file: " + error.getMessage(),
                    Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void openFolderPicker() {

        Intent intent = new Intent(
            Intent.ACTION_OPEN_DOCUMENT_TREE
        );

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );

        try {
            startActivityForResult(intent, REQUEST_PICK_FOLDER);
        } catch (Exception e) {
            Toast.makeText(
                this,
                "Unable to open folder picker.",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_PICK_FOLDER) {
            return;
        }

        if (resultCode != RESULT_OK || data == null ||
            data.getData() == null) {

            if (songs.isEmpty()) {
                libraryStatus.setText(
                    "Select a folder to load your MP3 music."
                );
            }
            return;
        }

        Uri treeUri = data.getData();

        int flags = data.getFlags()
            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            getContentResolver().takePersistableUriPermission(
                treeUri, flags
            );
        } catch (Exception e) {
            Toast.makeText(
                this,
                "Could not save folder access. Please select again.",
                Toast.LENGTH_LONG
            ).show();
            return;
        }

        getSharedPreferences("sound_library", MODE_PRIVATE)
            .edit()
            .putString("music_folder", treeUri.toString())
            .apply();

        loadFolder(treeUri);
    }

    private String getFolderName(Uri treeUri) {
        try {
            String documentId =
                DocumentsContract.getTreeDocumentId(treeUri);

            Uri documentUri =
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, documentId
                );

            try (Cursor cursor = getContentResolver().query(
                documentUri,
                new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                },
                null, null, null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    String name = cursor.getString(0);
                    if (name != null) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return "Selected music folder";
    }

    private List<Song> scanTree(Uri treeUri) {

        List<Song> found = new ArrayList<>();

        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        String rootDocumentId =
            DocumentsContract.getTreeDocumentId(treeUri);

        pending.push(rootDocumentId);

        String[] projection = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        while (!pending.isEmpty()) {

            String parentId = pending.pop();

            if (!visited.add(parentId)) {
                continue;
            }

            Uri childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, parentId
                );

            try (Cursor cursor = getContentResolver().query(
                childrenUri, projection, null, null, null
            )) {

                if (cursor == null) {
                    continue;
                }

                int idColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                );

                int nameColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                );

                int typeColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                );

                if (idColumn < 0 || nameColumn < 0 ||
                    typeColumn < 0) {
                    continue;
                }

                while (cursor.moveToNext()) {

                    String documentId =
                        cursor.getString(idColumn);

                    String name = cursor.getString(nameColumn);

                    String mime = cursor.getString(typeColumn);

                    if (documentId == null || name == null) {
                        continue;
                    }

                    if (DocumentsContract.Document.MIME_TYPE_DIR
                        .equals(mime)) {

                        pending.push(documentId);
                        continue;
                    }

                    if (name.toLowerCase(Locale.ROOT).endsWith(".mp3")) {

                        Uri documentUri =
                            DocumentsContract.buildDocumentUriUsingTree(
                                treeUri, documentId
                            );

                        found.add(new Song(name, documentUri));
                    }
                }
            }
        }

        found.sort(Comparator.comparing(
            song -> song.name.toLowerCase(Locale.ROOT)
        ));

        return found;
    }

    private void loadFolder(Uri treeUri) {

        folderLabel.setText("Folder: " + getFolderName(treeUri));
        folderButton.setText("↻   CHANGE MUSIC FOLDER");

        libraryStatus.setText("Scanning your MP3 collection...");

        songContainer.removeAllViews();

        scanExecutor.execute(() -> {

            List<Song> result = null;
            Exception scanError = null;

            try {
                result = scanTree(treeUri);
            } catch (Exception e) {
                scanError = e;
            }

            final List<Song> resultFinal = result;
            final Exception errorFinal = scanError;

            runOnUiThread(() -> {

                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (errorFinal != null) {

                    libraryStatus.setText(
                        "Folder access failed. Choose your folder again."
                    );

                    Toast.makeText(
                        MainActivity.this,
                        errorFinal.getMessage(),
                        Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                songs.clear();

                if (resultFinal != null) {
                    songs.addAll(resultFinal);
                }

                if (songs.isEmpty()) {
                    libraryStatus.setText(
                        "No MP3 files found. Choose another folder."
                    );
                } else {
                    libraryStatus.setText(
                        songs.size() + " MP3 tracks in your collection"
                    );
                }

                refreshSongRows();
            });
        });
    }

    private void refreshSongRows() {

        songContainer.removeAllViews();

        if (songs.isEmpty()) {

            TextView empty = makeText(
                "♫\n\nYour music library is empty.\n\n" +
                "Choose a folder containing MP3 files.",
                15,
                MUTED,
                false
            );

            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(35), dp(20), dp(35));

            songContainer.addView(empty);
            return;
        }

        int currentIndex = player != null
            ? player.getCurrentMediaItemIndex() : -1;

        for (int i = 0; i < songs.size(); i++) {

            final int index = i;
            Song song = songs.get(i);

            boolean selected = index == currentIndex;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(13), dp(13), dp(13), dp(13));

            row.setBackground(rounded(
                selected
                    ? Color.rgb(37, 57, 84)
                    : Color.rgb(20, 26, 39),
                13
            ));

            TextView musicSymbol = makeText(
                selected ? "♫" : "♪",
                22,
                selected ? ACCENT : PURPLE,
                true
            );

            musicSymbol.setGravity(Gravity.CENTER);

            row.addView(musicSymbol,
                new LinearLayout.LayoutParams(dp(38), -2));

            TextView title = makeText(
                song.name, 14,
                selected ? ACCENT : WHITE,
                selected
            );

            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);

            row.addView(title,
                new LinearLayout.LayoutParams(0, -2, 1f));

            TextView number = makeText(
                String.format(Locale.getDefault(), "%02d", i + 1),
                11,
                MUTED,
                false
            );

            row.addView(number);

            LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(-1, -2);

            rowParams.bottomMargin = dp(7);

            songContainer.addView(row, rowParams);

            row.setOnClickListener(v -> playSong(index));
        }
    }

    private void playSong(int index) {

        if (index < 0 || index >= songs.size() || player == null) {
            return;
        }

        List<MediaItem> items = new ArrayList<>();

        for (Song song : songs) {
            items.add(MediaItem.fromUri(song.uri));
        }

        player.setMediaItems(items, index, 0L);
        player.prepare();
        player.play();

        currentTitle.setText(songs.get(index).name);

        updatePlayButton();
        refreshSongRows();
    }

    private void togglePlayback() {

        if (player == null || player.getMediaItemCount() == 0) {
            Toast.makeText(
                this,
                "Choose a folder and select a song first.",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (player.isPlaying()) {
            player.pause();
        } else {
            if (player.getPlaybackState() == Player.STATE_ENDED) {
                player.seekTo(0);
            }
            player.play();
        }

        updatePlayButton();
    }

    private void previousTrack() {

        if (player == null || player.getMediaItemCount() == 0) {
            return;
        }

        if (player.getCurrentPosition() > 3000) {
            player.seekTo(0);
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem();
        } else {
            player.seekTo(0);
        }
    }

    private void nextTrack() {

        if (player == null || player.getMediaItemCount() == 0) {
            return;
        }

        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
        } else {
            player.seekTo(0);
            player.play();
        }
    }

    private void updatePlayButton() {

        if (playPauseButton == null || player == null) {
            return;
        }

        playPauseButton.setText(player.isPlaying() ? "Ⅱ" : "▶");
    }

    private String formatTime(long milliseconds) {

        if (milliseconds < 0) {
            milliseconds = 0;
        }

        long seconds = milliseconds / 1000;

        return String.format(
            Locale.getDefault(),
            "%d:%02d",
            seconds / 60,
            seconds % 60
        );
    }

    private void updateProgress() {

        if (player == null || seekBar == null) {
            return;
        }

        long position = player.getCurrentPosition();
        long duration = player.getDuration();

        if (duration == C.TIME_UNSET || duration <= 0) {
            duration = 0;
        }

        if (!userSeeking) {

            int progress = duration > 0
                ? (int) (position * 1000 / duration) : 0;

            seekBar.setProgress(
                Math.max(0, Math.min(1000, progress))
            );

            currentTime.setText(formatTime(position));
        }

        totalTime.setText(formatTime(duration));

        updatePlayButton();
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(progressUpdater);

        scanExecutor.shutdownNow();

        if (player != null) {
            player.release();
            player = null;
        }

        super.onDestroy();
    }
}