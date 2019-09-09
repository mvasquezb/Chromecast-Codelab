package com.google.sample.cast.refplayer.cast;

import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.sample.cast.refplayer.mediaplayer.LocalPlayerActivity;

public class CustomKSChannel implements Cast.MessageReceivedCallback {

    public String getNamespace() {
        return "urn:x-cast:co.karaokesmart";
    }

    @Override
    public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
        Log.d(LocalPlayerActivity.TAG, "Message received: " + message + " from: " + castDevice.getFriendlyName());
    }
}
