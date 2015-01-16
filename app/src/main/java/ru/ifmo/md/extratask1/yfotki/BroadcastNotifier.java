package ru.ifmo.md.extratask1.yfotki;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Created by Nikita Yaschenko on 16.01.15.
 */
public class BroadcastNotifier {

    private LocalBroadcastManager mManager;

    public BroadcastNotifier(Context context) {
        mManager = LocalBroadcastManager.getInstance(context);
    }

    public void broadcastIntentWithState(int status, int section) {
        Intent intent = new Intent();
        intent.setAction(Constants.BROADCAST_ACTION);
        intent.putExtra(Constants.EXTRA_STATUS, status);
        intent.putExtra(Constants.EXTRA_SECTION, section);
        intent.addCategory(Intent.CATEGORY_DEFAULT);

        mManager.sendBroadcast(intent);
    }

}
