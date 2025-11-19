package com.servidorzinho.installer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class ServerService extends Service {
    private static final String CHANNEL_ID = "mrit_server_channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (Exception e) {
            // Se falhar ao iniciar foreground, continua como serviço normal
            android.util.Log.e("ServerService", "Erro ao iniciar foreground: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Monitora o servidor e reinicia se necessário
        new Thread(() -> {
            while (true) {
                try {
                    // Verifica se o servidor está rodando via Termux API
                    checkAndStartServer();
                    Thread.sleep(30000); // Verifica a cada 30 segundos
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();

        return START_STICKY; // Reinicia se o serviço for morto
    }

    private void checkAndStartServer() {
        // Verifica se servidor está rodando e inicia se necessário
        try {
            Intent intent = new Intent("com.termux.EXECUTE");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.EXECUTE_COMMAND",
                    "cd ~/servidorzinho && bash iniciar_auto.sh");
            startService(intent);
        } catch (Exception e) {
            // Servidor não está rodando ou Termux não está disponível
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MRIT Server Local",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Servidorzinho")
                .setContentText("Servidor rodando em background")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
