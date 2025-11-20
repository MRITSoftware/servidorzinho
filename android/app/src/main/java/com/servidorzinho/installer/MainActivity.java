package com.servidorzinho.installer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private TextView statusText;
    private TextView commandText;
    private Button copyButton;
    private Button installButton;
    private Handler handler;
    private ClipboardManager clipboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(Looper.getMainLooper());
        statusText = findViewById(R.id.statusText);
        commandText = findViewById(R.id.commandText);
        copyButton = findViewById(R.id.copyButton);
        installButton = findViewById(R.id.installButton);
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        installButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                copyFiles();
            } else {
                requestPermissions();
            }
        });

        copyButton.setOnClickListener(v -> {
            String cmd = commandText.getText().toString();
            if (!cmd.isEmpty()) {
                copyToClipboard(cmd);
            }
        });

        checkStatus();
    }

    private boolean checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            copyFiles();
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE },
                PERMISSION_REQUEST_CODE);
    }

    private void checkStatus() {
        updateStatus("Pronto para copiar arquivos.\n\n" +
                "1. Clique em 'Copiar Arquivos'\n" +
                "2. Os comandos aparecerão abaixo\n" +
                "3. Copie e execute no Termux");
    }

    private void copyFiles() {
        installButton.setEnabled(false);
        updateStatus("Copiando arquivos...\n\nAguarde...");

        new Thread(() -> {
            try {
                // Copia para Download (acessível pelo Termux)
                File targetDir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "servidorzinho"
                );
                
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }

                copyAssetsToDir(targetDir);

                // Verifica se copiou
                File testFile = new File(targetDir, "servidor_auto.py");
                if (!testFile.exists()) {
                    throw new Exception("Erro: Arquivos não foram copiados");
                }

                // Comandos simples para o usuário executar
                String commands = 
                    "# Passo 1: Configurar storage do Termux\n" +
                    "termux-setup-storage\n\n" +
                    "# Passo 2: Copiar arquivos\n" +
                    "mkdir -p ~/servidorzinho\n" +
                    "cp -r ~/storage/downloads/servidorzinho/* ~/servidorzinho/\n" +
                    "cd ~/servidorzinho\n" +
                    "chmod +x *.sh *.py\n\n" +
                    "# Passo 3: Instalar dependências\n" +
                    "bash INSTALAR_AUTO.sh\n\n" +
                    "# Passo 4: Iniciar servidor\n" +
                    "bash iniciar_auto.sh\n";

                handler.post(() -> {
                    updateStatus("✅ Arquivos copiados para:\n" +
                            "~/storage/downloads/servidorzinho\n\n" +
                            "📋 Execute os comandos abaixo no Termux:");
                    commandText.setText(commands);
                    installButton.setText("Recopiar");
                    installButton.setEnabled(true);
                });

            } catch (Exception e) {
                handler.post(() -> {
                    updateStatus("❌ Erro: " + e.getMessage() + "\n\n" +
                            "Tente novamente.");
                    installButton.setEnabled(true);
                    Toast.makeText(this, "Erro: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void copyAssetsToDir(File targetDir) throws Exception {
        String[] files = {
                "servidor_auto.py",
                "iniciar_auto.sh",
                "parar.sh",
                "INSTALAR_AUTO.sh",
                "setup_boot.sh",
                "requirements.txt",
                "testar_servidor.sh"
        };

        for (String filename : files) {
            try {
                InputStream is = getAssets().open(filename);
                File outFile = new File(targetDir, filename);
                FileOutputStream fos = new FileOutputStream(outFile);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }

                fos.close();
                is.close();
                outFile.setReadable(true, false);
                outFile.setWritable(true, false);
                if (filename.endsWith(".sh") || filename.endsWith(".py")) {
                    outFile.setExecutable(true, false);
                }
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Erro ao copiar " + filename + ": " + e.getMessage());
            }
        }
    }

    private void copyToClipboard(String text) {
        ClipData clip = ClipData.newPlainText("Comando", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "✅ Comando copiado!", Toast.LENGTH_LONG).show();
    }

    private void updateStatus(String text) {
        statusText.setText(text);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                copyFiles();
            } else {
                Toast.makeText(this, "Permissão necessária", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
