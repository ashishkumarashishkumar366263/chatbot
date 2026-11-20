package com.rudra.botauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

// Phone reboot ke baad, agar engine chalu tha to service dobara start karo.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Store.isRunning(context)) return;
        Intent i = new Intent(context, BotService.class).setAction(BotService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i);
            else context.startService(i);
        } catch (Exception ignore) {}
    }
}
