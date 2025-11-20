package com.servidorzinho.installer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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
    private Button installButton;
    private Button openTermuxButton;
    private Button copyInstallButton;
    private Button copyStartButton;
    private Button copyStatusButton;
    private Button copyLogsButton;
    private Handler handler;
    private ClipboardManager clipboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(Looper.getMainLooper());
        statusText = findViewById(R.id.statusText);
        installButton = findViewById(R.id.installButton);
        openTermuxButton = findViewById(R.id.openTermuxButton);
        copyInstallButton = findViewById(R.id.copyInstallButton);
        copyStartButton = findViewById(R.id.copyStartButton);
        copyStatusButton = findViewById(R.id.copyStatusButton);
        copyLogsButton = findViewById(R.id.copyLogsButton);
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        installButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                startInstallation();
            } else {
                requestPermissions();
            }
        });

        openTermuxButton.setOnClickListener(v -> openTermux());

        copyInstallButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && bash INSTALAR_AUTO.sh");
            updateStatus("✅ Comando copiado!\n\n" +
                    "📱 No Termux:\n" +
                    "1. Cole o comando (Ctrl+Shift+V)\n" +
                    "2. Pressione Enter\n" +
                    "3. Aguarde a instalação (10-15 min)");
        });

        copyStartButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && bash iniciar_auto.sh");
            updateStatus("✅ Comando copiado!\n\n" +
                    "📱 No Termux:\n" +
                    "1. Cole o comando\n" +
                    "2. Pressione Enter\n" +
                    "3. O servidor iniciará");
        });

        copyStatusButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && curl -s http://localhost:8080/status || echo 'Servidor não está respondendo'");
            updateStatus("✅ Comando copiado!\n\n" +
                    "📱 No Termux:\n" +
                    "1. Cole o comando\n" +
                    "2. Pressione Enter");
        });

        copyLogsButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && tail -30 servidor.log");
            updateStatus("✅ Comando copiado!\n\n" +
                    "📱 No Termux:\n" +
                    "1. Cole o comando\n" +
                    "2. Pressione Enter");
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
            startInstallation();
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE },
                PERMISSION_REQUEST_CODE);
    }

    private void checkStatus() {
        updateStatus("Pronto para instalar.\n\n" +
                "1. Clique em 'Instalar' para copiar arquivos\n" +
                "2. O Termux será aberto automaticamente\n" +
                "3. Cole o comando que aparecerá");
    }

    private void startInstallation() {
        installButton.setEnabled(false);
        updateStatus("Iniciando instalação...\n\nAguarde...");

        new Thread(() -> {
            try {
                handler.post(() -> updateStatus("Copiando arquivos..."));
                copyFilesToTermux();

                handler.post(() -> {
                    updateStatus("✅ Arquivos copiados!\n\n" +
                            "📱 O Termux será aberto automaticamente.\n\n" +
                            "📋 Instruções:\n" +
                            "1. O comando já foi copiado\n" +
                            "2. No Termux, cole (Ctrl+Shift+V)\n" +
                            "3. Pressione Enter\n" +
                            "4. Aguarde a instalação (10-15 min)\n\n" +
                            "💡 Não feche o Termux durante a instalação!");
                    installButton.setText("Reinstalar");
                    installButton.setEnabled(true);
                });

                // Abre Termux após 1 segundo
                handler.postDelayed(() -> {
                    openTermux();
                }, 1000);

            } catch (Exception e) {
                handler.post(() -> {
                    updateStatus("❌ Erro: " + e.getMessage() + "\n\n" +
                            "Tente clicar em 'Instalar' novamente.");
                    installButton.setEnabled(true);
                    Toast.makeText(this, "Erro: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void openTermux() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (intent != null) {
                startActivity(intent);
                updateStatus("✅ Termux aberto!\n\n" +
                        "📋 Use os botões abaixo para copiar comandos\n" +
                        "💡 Cole com Ctrl+Shift+V ou long press");
            } else {
                updateStatus("❌ Termux não encontrado!\n\n" +
                        "Instale o Termux da Play Store primeiro.");
                installTermux();
            }
        } catch (Exception e) {
            updateStatus("❌ Erro ao abrir Termux: " + e.getMessage());
        }
    }

    private void installTermux() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=com.termux"));
            intent.setPackage("com.android.vending");
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.termux"));
                startActivity(intent);
            } catch (Exception e2) {
                updateStatus("⚠️ Não foi possível abrir a Play Store.\n\n" +
                        "Instale o Termux manualmente:\n" +
                        "https://play.google.com/store/apps/details?id=com.termux");
            }
        }
    }

    private void copyFilesToTermux() throws Exception {
        // Usa diretório do app (mais confiável)
        File targetDir = new File(getExternalFilesDir(null), "servidorzinho");
        
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        if (!targetDir.exists() || !targetDir.canWrite()) {
            throw new Exception("Não foi possível criar diretório: " + targetDir.getAbsolutePath());
        }

        copyAssetsToDir(targetDir);
        createSimpleCopyScript(targetDir);

        // Copia comando para área de transferência
        String installCommand = "mkdir -p ~/servidorzinho && " +
                "cp -r \"" + targetDir.getAbsolutePath() + "\"/* ~/servidorzinho/ && " +
                "cd ~/servidorzinho && " +
                "chmod +x *.sh *.py && " +
                "bash INSTALAR_AUTO.sh && " +
                "bash iniciar_auto.sh";

        handler.post(() -> copyToClipboard(installCommand));
    }

    private void createSimpleCopyScript(File sourceDir) throws Exception {
        // Cria script simples que copia tudo e instala
        File scriptFile = new File(sourceDir, "setup.sh");
        String scriptContent = "#!/bin/bash\n" +
                "clear\n" +
                "echo '╔══════════════════════════════════════╗'\n" +
                "echo '║   CONFIGURANDO SERVIDORZINHO        ║'\n" +
                "echo '╚══════════════════════════════════════╝'\n" +
                "echo ''\n" +
                "echo '📦 Copiando arquivos...'\n" +
                "mkdir -p ~/servidorzinho\n" +
                "SOURCE_DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"\n" +
                "cp -r \"$SOURCE_DIR\"/* ~/servidorzinho/ 2>/dev/null || true\n" +
                "rm ~/servidorzinho/setup.sh 2>/dev/null || true\n" +
                "chmod +x ~/servidorzinho/*.sh 2>/dev/null || true\n" +
                "chmod +x ~/servidorzinho/*.py 2>/dev/null || true\n" +
                "echo '✅ Arquivos copiados!'\n" +
                "echo ''\n" +
                "cd ~/servidorzinho\n" +
                "echo '📦 Instalando dependências...'\n" +
                "echo '⏳ Isso pode demorar 10-15 minutos'\n" +
                "echo '⏳ NÃO feche o Termux!'\n" +
                "echo ''\n" +
                "bash INSTALAR_AUTO.sh && {\n" +
                "    echo ''\n" +
                "    echo '🚀 Iniciando servidor...'\n" +
                "    bash iniciar_auto.sh\n" +
                "} || {\n" +
                "    echo ''\n" +
                "    echo '❌ Erro na instalação!'\n" +
                "    echo 'Tente executar manualmente:'\n" +
                "    echo '  cd ~/servidorzinho'\n" +
                "    echo '  bash INSTALAR_AUTO.sh'\n" +
                "}\n";

        FileOutputStream fos = new FileOutputStream(scriptFile);
        fos.write(scriptContent.getBytes());
        fos.close();
        scriptFile.setExecutable(true);
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
                startInstallation();
            } else {
                Toast.makeText(this, "Permissão necessária", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
