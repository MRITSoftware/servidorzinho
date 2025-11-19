package com.servidorzinho.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Inicia o serviço quando o dispositivo liga
            Intent serviceIntent = new Intent(context, ServerService.class);
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}
