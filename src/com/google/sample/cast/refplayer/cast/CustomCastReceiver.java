package com.google.sample.cast.refplayer.cast;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.media.MediaIntentReceiver;
import com.google.sample.cast.refplayer.mediaplayer.LocalPlayerActivity;

public class CustomCastReceiver extends MediaIntentReceiver {
    @Override
    protected void onReceiveActionMediaButton(Session session, Intent intent) {
        super.onReceiveActionMediaButton(session, intent);

        Log.d(LocalPlayerActivity.TAG, String.valueOf(intent.getData()));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        Log.d(LocalPlayerActivity.TAG, String.valueOf(intent.getData()));
    }
}
