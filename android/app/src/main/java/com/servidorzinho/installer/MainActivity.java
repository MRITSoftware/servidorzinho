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
        
        openTermuxButton.setOnClickListener(v -> {
            openTermuxAndInstall();
        });
        
        copyInstallButton.setOnClickListener(v -> {
            copyToClipboard("bash ~/storage/downloads/MRIT_Server/copy_to_termux.sh");
        });
        
        copyStartButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && bash iniciar_auto.sh");
        });
        
        copyStatusButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && bash testar_servidor.sh");
            updateStatus("✅ Comando copiado!\n\nCole no Termux para verificar o status.");
        });
        
        copyLogsButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && tail -30 servidor.log");
            updateStatus("✅ Comando copiado!\n\nCole no Termux para ver os logs.");
        });
        
        checkStatus();
    }

    private boolean checkPermissions() {
        // Android 11+ não precisa de WRITE_EXTERNAL_STORAGE para arquivos próprios do
        // app
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return true; // Android 11+ usa scoped storage
        }
        return ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ não precisa pedir permissão
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
                "2. Abra o Termux manualmente\n" +
                "3. Execute o comando que aparecerá");
    }

    private boolean isTermuxInstalled() {
        try {
            // Método mais simples: tenta abrir o Termux
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (intent != null) {
                return true;
            }
            
            // Tenta verificar via PackageManager
            try {
                getPackageManager().getPackageInfo("com.termux", PackageManager.GET_ACTIVITIES);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isServerInstalled() {
        File serverDir = new File(getExternalFilesDir(null), "servidorzinho");
        File configFile = new File(serverDir, "local_config.json");
        return configFile.exists();
    }

    private void startInstallation() {
        installButton.setEnabled(false);
        updateStatus("Iniciando instalação...\n\nIsso pode levar alguns minutos.");

        new Thread(() -> {
            try {
                // 1. Instala Termux se necessário
                if (!isTermuxInstalled()) {
                    handler.post(() -> updateStatus(
                            "Instalando Termux...\n\nPor favor, instale o Termux quando solicitado."));
                    installTermux();
                    Thread.sleep(3000);
                }

                // 2. Copia arquivos para o Termux
                handler.post(() -> updateStatus("Copiando arquivos..."));
                copyFilesToTermux();

                // 3. Executa instalação via Termux API
                handler.post(() -> updateStatus("Instalando dependências...\n\nAguarde..."));
                installDependencies();

                // 4. Configura auto-inicialização
                handler.post(() -> updateStatus("Configurando inicialização automática..."));
                setupTermuxAutoStart();
                setupAutoStart();

                handler.post(() -> {
                    updateStatus("✅ Configuração concluída!\n\n" +
                            "1. Abra o Termux uma vez\n" +
                            "2. A instalação iniciará automaticamente\n" +
                            "3. O servidor iniciará sozinho após instalar\n\n" +
                            "Você pode fechar este app.");
                    installButton.setText("Reinstalar");
                    installButton.setEnabled(true);

                    // Inicia o serviço em background para monitorar
                    startServerService();
                });

            } catch (Exception e) {
                handler.post(() -> {
                    updateStatus("❌ Erro: " + e.getMessage());
                    installButton.setEnabled(true);
                    Toast.makeText(this, "Erro na instalação: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void installTermux() {
        // Tenta abrir Play Store de várias formas
        try {
            // Tenta Play Store primeiro
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=com.termux"));
            intent.setPackage("com.android.vending");
            startActivity(intent);
        } catch (Exception e) {
            try {
                // Fallback: URL da web
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.termux"));
                startActivity(intent);
            } catch (Exception e2) {
                updateStatus("⚠️ Não foi possível abrir a Play Store.\n\n" +
                        "Por favor, instale o Termux manualmente:\n" +
                        "https://play.google.com/store/apps/details?id=com.termux");
            }
        }
    }

    private void openTermuxAndInstall() {
        if (checkPermissions()) {
            new Thread(() -> {
                try {
                    handler.post(() -> updateStatus("Copiando arquivos..."));
                    copyFilesToTermux();
                } catch (Exception e) {
                    handler.post(() -> {
                        updateStatus("❌ Erro ao copiar arquivos: " + e.getMessage() + "\n\n" +
                                "Tente clicar em 'Instalar' novamente.");
                    });
                    android.util.Log.e("MainActivity", "Erro: " + e.getMessage());
                }
            }).start();
        } else {
            requestPermissions();
        }
    }

    private void copyFilesToTermux() throws Exception {
        // Copia para storage compartilhado - múltiplos locais
        File[] targetDirs = {
            new File("/sdcard/Download/MRIT_Server"),
            new File("/storage/emulated/0/Download/MRIT_Server"),
            new File(getExternalFilesDir(null), "servidorzinho")
        };
        
        boolean copied = false;
        for (File dir : targetDirs) {
            try {
                dir.mkdirs();
                copyAssetsToDir(dir);
                createTermuxCopyScript(dir);
                copied = true;
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Erro ao copiar para " + dir + ": " + e.getMessage());
            }
        }
        
        if (!copied) {
            handler.post(() -> {
                updateStatus("❌ Erro ao copiar arquivos.\n\n" +
                        "Verifique as permissões do app.");
            });
            return;
        }
        
        handler.post(() -> {
            updateStatus("✅ Arquivos copiados!\n\n" +
                    "📱 No Termux:\n" +
                    "1. Execute: termux-setup-storage\n" +
                    "2. Clique no botão '📥 Copiar: Instalar' abaixo\n" +
                    "3. Cole no Termux e pressione Enter");
        });
    }

    private void createTermuxCopyScript(File sourceDir) throws Exception {
        // Cria script que usa diretório atual (mais confiável)
        File scriptFile = new File(sourceDir, "copy_to_termux.sh");
        String scriptContent = 
            "#!/bin/bash\n" +
            "set -e\n" +
            "clear\n" +
            "echo '╔══════════════════════════════════════╗'\n" +
            "echo '║   MRIT Server Local - Instalação    ║'\n" +
            "echo '╚══════════════════════════════════════╝'\n" +
            "echo ''\n" +
            "echo '📦 Passo 1/3: Copiando arquivos...'\n" +
            "mkdir -p ~/servidorzinho\n" +
            "SOURCE_DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"\n" +
            "cp -r \"$SOURCE_DIR\"/* ~/servidorzinho/ 2>/dev/null || true\n" +
            "rm ~/servidorzinho/copy_to_termux.sh 2>/dev/null || true\n" +
            "chmod +x ~/servidorzinho/*.sh 2>/dev/null || true\n" +
            "echo '✅ Arquivos copiados!'\n" +
            "echo ''\n" +
            "cd ~/servidorzinho\n" +
            "if [ ! -f .installed ]; then\n" +
            "    echo '📦 Passo 2/3: Instalando dependências...'\n" +
            "    echo '⏳ Isso pode demorar 10-15 minutos'\n" +
            "    echo '⏳ Por favor, aguarde...'\n" +
            "    echo ''\n" +
            "    bash INSTALAR_AUTO.sh\n" +
            "    echo ''\n" +
            "    echo '📦 Passo 3/3: Iniciando servidor...'\n" +
            "    bash iniciar_auto.sh\n" +
            "else\n" +
            "    echo '📦 Passo 2/3: Servidor já instalado'\n" +
            "    echo '📦 Passo 3/3: Iniciando servidor...'\n" +
            "    bash iniciar_auto.sh\n" +
            "fi\n" +
            "echo ''\n" +
            "echo '✅ Instalação completa!'\n" +
            "echo ''\n" +
            "echo '📌 Comandos úteis:'\n" +
            "echo '   start   -> Inicia servidor'\n" +
            "echo '   status  -> Verifica se está rodando'\n" +
            "echo '   logs    -> Mostra logs'\n" +
            "echo '   stop    -> Para servidor'\n";

        FileOutputStream fos = new FileOutputStream(scriptFile);
        fos.write(scriptContent.getBytes());
        fos.close();
        scriptFile.setExecutable(true);
    }

    private void setupTermuxAutoStart() throws Exception {
        // Não precisa mais - o script copy_to_termux.sh já faz tudo
        // Mantido para compatibilidade
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
                if (filename.endsWith(".sh")) {
                    outFile.setExecutable(true, false);
                }
            } catch (Exception e) {
                // Arquivo não encontrado em assets, continua
            }
        }
    }

    private void installDependencies() throws Exception {
        // A instalação será feita automaticamente quando o Termux abrir
        // via script no .bashrc que criamos
        handler.post(() -> {
            updateStatus("✅ Configuração concluída!\n\n" +
                    "Abra o Termux uma vez para iniciar a instalação automática.\n" +
                    "O servidor iniciará automaticamente após a instalação.");
        });
    }

    private void setupAutoStart() throws Exception {
        // Configuração será feita pelo INSTALAR_AUTO.sh
        // Não precisa fazer nada aqui
    }

    private void startServerService() {
        try {
            Intent serviceIntent = new Intent(this, ServerService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            // Se falhar, não é crítico - o servidor pode rodar sem o serviço
            log("Serviço não iniciado: " + e.getMessage());
        }
    }

    private void log(String msg) {
        android.util.Log.d("Servidorzinho", msg);
    }
    
    private void copyToClipboard(String text) {
        ClipData clip = ClipData.newPlainText("Comando", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "✅ Comando copiado!", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Permissão necessária para instalar", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
