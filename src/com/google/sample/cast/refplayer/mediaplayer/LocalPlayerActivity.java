/*
 * Copyright (C) 2016 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sample.cast.refplayer.mediaplayer;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.cast.CustomKSChannel;
import com.google.sample.cast.refplayer.expandedcontrols.ExpandedControlsActivity;
import com.google.sample.cast.refplayer.settings.CastPreference;
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest;
import com.google.sample.cast.refplayer.utils.MediaItem;
import com.google.sample.cast.refplayer.utils.Utils;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Activity for the local media player.
 */
public class LocalPlayerActivity extends AppCompatActivity {

    public static final String TAG = LocalPlayerActivity.class.getSimpleName();
    private VideoView mVideoView;
    private TextView mTitleView;
    private TextView mDescriptionView;
    private TextView mStartText;
    private TextView mEndText;
    private SeekBar mSeekbar;
    private ImageView mPlayPause;
    private ProgressBar mLoading;
    private View mControllers;
    private View mContainer;
    private NetworkImageView mCoverArt;
    private Timer mSeekbarTimer;
    private Timer mControllersTimer;
    private PlaybackState mPlaybackState;
    private final Handler mHandler = new Handler();
    private final float mAspectRatio = 72f / 128;
    private MediaItem mSelectedMedia;
    private boolean mControllersVisible;
    private int mDuration;
    private TextView mAuthorView;
    private ImageButton mPlayCircle;
    private PlaybackLocation mLocation;
    private CastContext mCastContext;
    private MenuItem mediaRouteMenuItem;
    private SessionManagerListener<CastSession> mSessionManagerListener;
    private CastSession mCastSession;
    private MediaQueue mQueue;
    private RemoteMediaClient mRemoteMediaClient;
    private CustomKSChannel mKsChannel;

    public RemoteMediaClient getRemoteMediaClient() {
        if (mCastSession != null) {
            mRemoteMediaClient = mCastSession.getRemoteMediaClient();
        }
        return mRemoteMediaClient;
    }

    /**
     * indicates whether we are doing a local or a remote playback
     */
    public enum PlaybackLocation {
        LOCAL,
        REMOTE
    }

    /**
     * List of various states that we can be in
     */
    public enum PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player_activity);
        loadViews();
        setupControlsCallbacks();
        setupCastListener();
        mCastContext = CastContext.getSharedInstance(this);
        mCastSession = mCastContext.getSessionManager().getCurrentCastSession();
        mKsChannel = new CustomKSChannel();
        if (mCastSession != null && mCastSession.isConnected()) {
            mRemoteMediaClient = mCastSession.getRemoteMediaClient();
            mQueue = getRemoteMediaClient().getMediaQueue();
            getRemoteMediaClient().registerCallback(new RemoteMediaClient.Callback() {
                @Override
                public void onStatusUpdated() {
                    Intent intent = new Intent(LocalPlayerActivity.this, ExpandedControlsActivity.class);
                    startActivity(intent);
                    getRemoteMediaClient().unregisterCallback(this);
                }
            });
        }

        // see what we need to play and where
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            mSelectedMedia = MediaItem.fromBundle(getIntent().getBundleExtra("media"));
            setupActionBar();
            boolean shouldStartPlayback = bundle.getBoolean("shouldStart");
            int startPosition = bundle.getInt("startPosition", 0);
            mVideoView.setVideoURI(Uri.parse(mSelectedMedia.getUrl()));
            Log.d(TAG, "Setting url of the VideoView to: " + mSelectedMedia.getUrl());
            if (shouldStartPlayback) {
                // this will be the case only if we are coming from the
                // CastControllerActivity by disconnecting from a device
                mPlaybackState = PlaybackState.PLAYING;
                updatePlaybackLocation(PlaybackLocation.LOCAL);
                updatePlayButton(mPlaybackState);
                if (startPosition > 0) {
                    mVideoView.seekTo(startPosition);
                }
                mVideoView.start();
                startControllersTimer();
            } else {
                if (mCastSession != null && mCastSession.isConnected()) {
                    updatePlaybackLocation(PlaybackLocation.REMOTE);
                } else {
                    // we should load the video but pause it
                    // and show the album art.
                    updatePlaybackLocation(PlaybackLocation.LOCAL);
                }
                mPlaybackState = PlaybackState.IDLE;
                updatePlayButton(mPlaybackState);
            }
        }
        if (mTitleView != null) {
            updateMetadata(true);
        }
    }

    private void updatePlaybackLocation(PlaybackLocation location) {
        Log.d(LocalPlayerActivity.TAG, "updatePlaybackLocation. " + location);
        mLocation = location;
        if (location == PlaybackLocation.LOCAL) {
            if (mPlaybackState == PlaybackState.PLAYING
                    || mPlaybackState == PlaybackState.BUFFERING) {
                setCoverArtStatus(null);
                startControllersTimer();
            } else {
                stopControllersTimer();
                setCoverArtStatus(mSelectedMedia.getImage(0));
            }
        } else {
            stopControllersTimer();
            setCoverArtStatus(mSelectedMedia.getImage(0));
            updateControllersVisibility(false);
        }
    }

    private void play(int position) {
        startControllersTimer();
        switch (mLocation) {
            case LOCAL:
                mVideoView.seekTo(position);
                mVideoView.start();
                break;
            case REMOTE:
                mPlaybackState = PlaybackState.BUFFERING;
                updatePlayButton(mPlaybackState);
                mCastSession.getRemoteMediaClient().seek(position);
                break;
            default:
                break;
        }
        restartTrickplayTimer();
    }

    private void togglePlayback() {
        stopControllersTimer();
        switch (mPlaybackState) {
            case PAUSED:
                switch (mLocation) {
                    case LOCAL:
                        mVideoView.start();
                        Log.d(TAG, "Playing locally...");
                        mPlaybackState = PlaybackState.PLAYING;
                        startControllersTimer();
                        restartTrickplayTimer();
                        updatePlaybackLocation(PlaybackLocation.LOCAL);
                        break;
                    case REMOTE:
                        finish();
                        break;
                    default:
                        break;
                }
                break;

            case PLAYING:
                mPlaybackState = PlaybackState.PAUSED;
                mVideoView.pause();
                break;

            case IDLE:
                Log.d(LocalPlayerActivity.TAG, "togglePlayback. IDLE");
                switch (mLocation) {
                    case LOCAL:
                        Log.d(LocalPlayerActivity.TAG, "togglePlayback. IDLE - LOCAL");
                        mVideoView.setVideoURI(Uri.parse(mSelectedMedia.getUrl()));
                        mVideoView.seekTo(0);
                        mVideoView.start();
                        mPlaybackState = PlaybackState.PLAYING;
                        restartTrickplayTimer();
                        updatePlaybackLocation(PlaybackLocation.LOCAL);
                        break;
                    case REMOTE:
                        Log.d(LocalPlayerActivity.TAG, "togglePlayback. IDLE - REMOTE");
                        if (mCastSession != null && mCastSession.isConnected()) {
                            Log.d(LocalPlayerActivity.TAG, "togglePlayback. IDLE - REMOTE. Remote media loaded");
                            loadRemoteMedia(mSeekbar.getProgress(), true);
                        }
                        break;
                    default:
                        Log.d(LocalPlayerActivity.TAG, "togglePlayback. IDLE - DEFAULT");
                        break;
                }
                break;
            default:
                break;
        }
        updatePlayButton(mPlaybackState);
    }

    private void setCoverArtStatus(String url) {
        if (url != null) {
            ImageLoader mImageLoader = CustomVolleyRequest.getInstance(this.getApplicationContext())
                    .getImageLoader();
            mImageLoader.get(url, ImageLoader.getImageListener(mCoverArt, 0, 0));
            mCoverArt.setImageUrl(url, mImageLoader);

            mCoverArt.setVisibility(View.VISIBLE);
            mVideoView.setVisibility(View.INVISIBLE);
        } else {
            mCoverArt.setVisibility(View.GONE);
            mVideoView.setVisibility(View.VISIBLE);
        }
    }

    private void stopTrickplayTimer() {
        Log.d(TAG, "Stopped TrickPlay Timer");
        if (mSeekbarTimer != null) {
            mSeekbarTimer.cancel();
        }
    }

    private void restartTrickplayTimer() {
        stopTrickplayTimer();
        mSeekbarTimer = new Timer();
        mSeekbarTimer.scheduleAtFixedRate(new UpdateSeekbarTask(), 100, 1000);
        Log.d(TAG, "Restarted TrickPlay Timer");
    }

    private void stopControllersTimer() {
        if (mControllersTimer != null) {
            mControllersTimer.cancel();
        }
    }

    private void startControllersTimer() {
        if (mControllersTimer != null) {
            mControllersTimer.cancel();
        }
        if (mLocation == PlaybackLocation.REMOTE) {
            return;
        }
        mControllersTimer = new Timer();
        mControllersTimer.schedule(new HideControllersTask(), 5000);
    }

    // should be called from the main thread
    private void updateControllersVisibility(boolean show) {
        if (show) {
            getSupportActionBar().show();
            mControllers.setVisibility(View.VISIBLE);
        } else {
            if (!Utils.isOrientationPortrait(this)) {
                getSupportActionBar().hide();
            }
            mControllers.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() was called");
        if (mLocation == PlaybackLocation.LOCAL) {

            if (mSeekbarTimer != null) {
                mSeekbarTimer.cancel();
                mSeekbarTimer = null;
            }
            if (mControllersTimer != null) {
                mControllersTimer.cancel();
            }
            // since we are playing locally, we need to stop the playback of
            // video (if user is not watching, pause it!)
            mVideoView.pause();
            mPlaybackState = PlaybackState.PAUSED;
            updatePlayButton(mPlaybackState);
        }
        mCastContext.getSessionManager().removeSessionManagerListener(mSessionManagerListener,
                CastSession.class);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop() was called");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy() is called");
        stopControllersTimer();
        stopTrickplayTimer();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart was called");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume() was called");
        mCastContext.getSessionManager().addSessionManagerListener(mSessionManagerListener,
                CastSession.class);
        if (mCastSession != null && mCastSession.isConnected()) {
            updatePlaybackLocation(PlaybackLocation.REMOTE);
        } else {
            updatePlaybackLocation(PlaybackLocation.LOCAL);
        }
        Intent intent = getIntent();
        Uri intentToJoinUri = Uri.parse("https://karaokesmart.co/cast/join");
        if (intent.getData() != null && intent.getData().equals(intentToJoinUri)) {
            mCastContext.getSessionManager().startSession(intent);
        }
        super.onResume();
    }

    private class HideControllersTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateControllersVisibility(false);
                    mControllersVisible = false;
                }
            });

        }
    }

    private class UpdateSeekbarTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (mLocation == PlaybackLocation.LOCAL) {
                        int currentPos = mVideoView.getCurrentPosition();
                        updateSeekbar(currentPos, mDuration);
                    }
                }
            });
        }
    }

    private void setupControlsCallbacks() {
        mVideoView.setOnErrorListener(new OnErrorListener() {

            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "OnErrorListener.onError(): VideoView encountered an "
                        + "error, what: " + what + ", extra: " + extra);
                String msg;
                if (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
                    msg = getString(R.string.video_error_media_load_timeout);
                } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                    msg = getString(R.string.video_error_server_unaccessible);
                } else {
                    msg = getString(R.string.video_error_unknown_error);
                }
                Utils.showErrorDialog(LocalPlayerActivity.this, msg);
                mVideoView.stopPlayback();
                mPlaybackState = PlaybackState.IDLE;
                updatePlayButton(mPlaybackState);
                return true;
            }
        });

        mVideoView.setOnPreparedListener(new OnPreparedListener() {

            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG, "onPrepared is reached");
                mDuration = mp.getDuration();
                mEndText.setText(Utils.formatMillis(mDuration));
                mSeekbar.setMax(mDuration);
                restartTrickplayTimer();
            }
        });

        mVideoView.setOnCompletionListener(new OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                stopTrickplayTimer();
                Log.d(TAG, "setOnCompletionListener()");
                mPlaybackState = PlaybackState.IDLE;
                updatePlayButton(mPlaybackState);
            }
        });

        mVideoView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!mControllersVisible) {
                    updateControllersVisibility(true);
                }
                startControllersTimer();
                return false;
            }
        });

        mSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mPlaybackState == PlaybackState.PLAYING) {
                    play(seekBar.getProgress());
                } else if (mPlaybackState != PlaybackState.IDLE) {
                    mVideoView.seekTo(seekBar.getProgress());
                }
                startControllersTimer();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopTrickplayTimer();
                mVideoView.pause();
                stopControllersTimer();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                mStartText.setText(Utils.formatMillis(progress));
            }
        });

        mPlayPause.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mLocation == PlaybackLocation.LOCAL) {
                    togglePlayback();
                }
            }
        });
    }

    private void updateSeekbar(int position, int duration) {
        mSeekbar.setProgress(position);
        mSeekbar.setMax(duration);
        mStartText.setText(Utils.formatMillis(position));
        mEndText.setText(Utils.formatMillis(duration));
    }

    private void updatePlayButton(PlaybackState state) {
        Log.d(TAG, "Controls: PlayBackState: " + state);
        boolean isConnected = (mCastSession != null)
                && (mCastSession.isConnected() || mCastSession.isConnecting());
        mControllers.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        mPlayCircle.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        switch (state) {
            case PLAYING:
                mLoading.setVisibility(View.INVISIBLE);
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(
                        getResources().getDrawable(R.drawable.ic_av_pause_dark));
                mPlayCircle.setVisibility(isConnected ? View.VISIBLE : View.GONE);
                break;
            case IDLE:
                mPlayCircle.setVisibility(View.VISIBLE);
                mControllers.setVisibility(View.GONE);
                mCoverArt.setVisibility(View.VISIBLE);
                mVideoView.setVisibility(View.INVISIBLE);
                break;
            case PAUSED:
                mLoading.setVisibility(View.INVISIBLE);
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(
                        getResources().getDrawable(R.drawable.ic_av_play_dark));
                mPlayCircle.setVisibility(isConnected ? View.VISIBLE : View.GONE);
                break;
            case BUFFERING:
                mPlayPause.setVisibility(View.INVISIBLE);
                mLoading.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getSupportActionBar().show();
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
            updateMetadata(false);
            mContainer.setBackgroundColor(getResources().getColor(R.color.black));

        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
            updateMetadata(true);
            mContainer.setBackgroundColor(getResources().getColor(R.color.white));
        }
    }

    private void updateMetadata(boolean visible) {
        Point displaySize;
        if (!visible) {
            mDescriptionView.setVisibility(View.GONE);
            mTitleView.setVisibility(View.GONE);
            mAuthorView.setVisibility(View.GONE);
            displaySize = Utils.getDisplaySize(this);
            RelativeLayout.LayoutParams lp = new
                    RelativeLayout.LayoutParams(displaySize.x,
                    displaySize.y + getSupportActionBar().getHeight());
            lp.addRule(RelativeLayout.CENTER_IN_PARENT);
            mVideoView.setLayoutParams(lp);
            mVideoView.invalidate();
        } else {
            mDescriptionView.setText(mSelectedMedia.getSubTitle());
            mTitleView.setText(mSelectedMedia.getTitle());
            mAuthorView.setText(mSelectedMedia.getStudio());
            mDescriptionView.setVisibility(View.VISIBLE);
            mTitleView.setVisibility(View.VISIBLE);
            mAuthorView.setVisibility(View.VISIBLE);
            displaySize = Utils.getDisplaySize(this);
            RelativeLayout.LayoutParams lp = new
                    RelativeLayout.LayoutParams(displaySize.x,
                    (int) (displaySize.x * mAspectRatio));
            lp.addRule(RelativeLayout.BELOW, R.id.toolbar);
            mVideoView.setLayoutParams(lp);
            mVideoView.invalidate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.browse, menu);
        mediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        if (item.getItemId() == R.id.action_settings) {
            intent = new Intent(LocalPlayerActivity.this, CastPreference.class);
            startActivity(intent);
        } else if (item.getItemId() == android.R.id.home) {
            ActivityCompat.finishAfterTransition(this);
        }
        return true;
    }

    private void setupActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(mSelectedMedia.getTitle());
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void loadViews() {
        mVideoView = (VideoView) findViewById(R.id.videoView1);
        mTitleView = (TextView) findViewById(R.id.textView1);
        mDescriptionView = (TextView) findViewById(R.id.textView2);
        mDescriptionView.setMovementMethod(new ScrollingMovementMethod());
        mAuthorView = (TextView) findViewById(R.id.textView3);
        mStartText = (TextView) findViewById(R.id.startText);
        mStartText.setText(Utils.formatMillis(0));
        mEndText = (TextView) findViewById(R.id.endText);
        mSeekbar = (SeekBar) findViewById(R.id.seekBar1);
        mPlayPause = (ImageView) findViewById(R.id.imageView2);
        mLoading = (ProgressBar) findViewById(R.id.progressBar1);
        mControllers = findViewById(R.id.controllers);
        mContainer = findViewById(R.id.container);
        mCoverArt = (NetworkImageView) findViewById(R.id.coverArtView);
        ViewCompat.setTransitionName(mCoverArt, getString(R.string.transition_image));
        mPlayCircle = (ImageButton) findViewById(R.id.play_circle);
        mPlayCircle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCastSession != null && mCastSession.isConnected()) {
                    if (mQueue == null || mQueue.getItemCount() == 0) {
                        getRemoteMediaClient().queueInsertAndPlayItem(mSelectedMedia.toMediaQueueItem(),
                                0, null);
                        togglePlayback();
                    } else {
                        showEnqueueDialog();
                    }
                } else {
                    togglePlayback();
                }
            }
        });
    }

    private void showEnqueueDialog() {
        final Dialog enqueueDialog = new AlertDialog.Builder(this)
                .setMessage("Play now or add to queue ?")
                .setPositiveButton("Add to queue", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        getRemoteMediaClient().queueAppendItem(mSelectedMedia.toMediaQueueItem(), null);
                        Toast.makeText(LocalPlayerActivity.this,
                                "\"" + mSelectedMedia.getTitle() + "\" added to queue",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Play now", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        loadRemoteMedia(0, true);
                    }
                })
                .create();
        enqueueDialog.show();
    }

    private void setupCastListener() {
        mSessionManagerListener = new SessionManagerListener<CastSession>() {

            @Override
            public void onSessionStarting(CastSession castSession) {
            }

            @Override
            public void onSessionStarted(CastSession castSession, String s) {
                onApplicationConnected(castSession);
            }

            @Override
            public void onSessionStartFailed(CastSession castSession, int i) {
                onApplicationDisconnected();
            }

            @Override
            public void onSessionEnding(CastSession castSession) {

            }

            @Override
            public void onSessionEnded(CastSession castSession, int i) {
                onApplicationDisconnected();
            }

            @Override
            public void onSessionResuming(CastSession castSession, String s) {

            }

            @Override
            public void onSessionResumed(CastSession castSession, boolean b) {
                onApplicationConnected(castSession);
            }

            @Override
            public void onSessionResumeFailed(CastSession castSession, int i) {

            }

            @Override
            public void onSessionSuspended(CastSession castSession, int i) {

            }

            private void onApplicationConnected(CastSession castSession) {
                Log.d(LocalPlayerActivity.TAG, "onApplicationConnected");
                mCastSession = castSession;
                mCastSession.sendMessage(getBaseContext().getString(R.string.cast_namespace), "connection");
                try {
                    mCastSession.setMessageReceivedCallbacks(
                            mKsChannel.getNamespace(),
                            mKsChannel
                    );
                } catch (Exception e) {
                    Log.e(LocalPlayerActivity.TAG, "Error creating custom channel");
                }
                Log.d(LocalPlayerActivity.TAG, "onApplicationConnected. selectedMedia: " + mSelectedMedia);
                if (null != mSelectedMedia) {
                    Log.d(LocalPlayerActivity.TAG, "onApplicationConnected. selectedMedia not null: " + mSelectedMedia);
                    if (mPlaybackState == PlaybackState.PLAYING) {
                        mVideoView.pause();
                        loadRemoteMedia(mSeekbar.getProgress(), true);
                        return;
                    } else {
                        Log.d(LocalPlayerActivity.TAG, "onApplicationConnected. selectedMedia null");
                        mPlaybackState = PlaybackState.IDLE;
                        updatePlaybackLocation(PlaybackLocation.REMOTE);
                    }
                }
                updatePlayButton(mPlaybackState);
                supportInvalidateOptionsMenu();
            }

            private void onApplicationDisconnected() {
                if (mCastSession != null) {
                    try {
                        mCastSession.removeMessageReceivedCallbacks(mKsChannel.getNamespace());
                    } catch (Exception e) {
                        Log.e(LocalPlayerActivity.TAG, "Error creating custom channel");
                    }
                }
                updatePlaybackLocation(PlaybackLocation.LOCAL);
                mPlaybackState = PlaybackState.IDLE;
                mLocation = PlaybackLocation.LOCAL;
                updatePlayButton(mPlaybackState);
                supportInvalidateOptionsMenu();
            }
        };
    }

    private void loadRemoteMedia(int seekPosition, boolean autoPlay) {
        Log.d(LocalPlayerActivity.TAG, "loadRemoteMedia");
        if (mCastSession == null) {
            Log.d(LocalPlayerActivity.TAG, "loadRemoteMedia. CastSession null");
            return;
        }
        final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        if (remoteMediaClient == null) {
            Log.d(LocalPlayerActivity.TAG, "loadRemoteMedia remoteMediaClient null");
            return;
        }

        Log.d(LocalPlayerActivity.TAG, "loadRemoteMedia. media loaded");
        mCastSession.sendMessage(mKsChannel.getNamespace(), "connection")
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        Log.d(LocalPlayerActivity.TAG, "sendMessage onResult: " + status.isSuccess());
                    }
                });
//        remoteMediaClient.queueInsertAndPlayItem(mSelectedMedia.toMediaQueueItem(), 0, null);
        remoteMediaClient.load(new MediaLoadRequestData.Builder()
                .setMediaInfo(mSelectedMedia.getMediaInfo())
                .setAutoplay(autoPlay)
                .setCurrentTime(seekPosition)
                .build());

        Toast.makeText(LocalPlayerActivity.this,
                "Now playing \"" + mSelectedMedia.getTitle() + "\"",
                Toast.LENGTH_SHORT).show();
    }
}
