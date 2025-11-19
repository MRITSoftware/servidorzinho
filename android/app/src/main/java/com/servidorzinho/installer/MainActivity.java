package com.servidorzinho.installer;

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
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(Looper.getMainLooper());
        statusText = findViewById(R.id.statusText);
        installButton = findViewById(R.id.installButton);
        openTermuxButton = findViewById(R.id.openTermuxButton);
        
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
        updateStatus("Verificando instalação...");

        // Verifica se Termux está instalado
        if (isTermuxInstalled()) {
            // Verifica se servidor já está instalado
            if (isServerInstalled()) {
                updateStatus("✅ Servidor já instalado!\n\nInicie o Termux para rodar o servidor.");
                installButton.setText("Reinstalar");
            } else {
                updateStatus("Termux encontrado. Pronto para instalar.");
            }
        } else {
            updateStatus("⚠️ Termux não encontrado.\n\nSerá necessário instalar o Termux primeiro.");
            installButton.setText("Instalar Termux e Servidor");
        }
    }

    private boolean isTermuxInstalled() {
        try {
            // Tenta diferentes package names do Termux
            String[] termuxPackages = {
                    "com.termux",
                    "com.termux.api", // Termux API (versão alternativa)
                    "com.termux.boot" // Termux Boot
            };

            for (String pkg : termuxPackages) {
                try {
                    getPackageManager().getPackageInfo(pkg, 0);
                    return true;
                } catch (PackageManager.NameNotFoundException e) {
                    // Continua tentando
                }
            }

            // Verifica se Termux está instalado via Intent
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (intent != null) {
                return true;
            }

            return false;
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
        // Abre Play Store para instalar Termux
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://details?id=com.termux"));
        startActivity(intent);
    }
    
    private void openTermuxAndInstall() {
        // Primeiro, garante que a instalação foi feita
        if (checkPermissions()) {
            new Thread(() -> {
                try {
                    // Copia arquivos se ainda não foram copiados
                    File termuxHome = new File("/data/data/com.termux/files/home");
                    File termuxDir = new File(termuxHome, "servidorzinho");
                    if (!termuxDir.exists() || termuxDir.listFiles() == null || termuxDir.listFiles().length == 0) {
                        handler.post(() -> updateStatus("Copiando arquivos..."));
                        copyFilesToTermux();
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Erro ao copiar arquivos: " + e.getMessage());
                }
                
                // Abre o Termux
                handler.post(() -> {
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            updateStatus("✅ Termux aberto!\n\n" +
                                    "A instalação iniciará automaticamente.\n" +
                                    "Aguarde alguns segundos...");
                        } else {
                            // Se Termux não estiver instalado, abre Play Store
                            installTermux();
                            updateStatus("⚠️ Termux não encontrado.\n\n" +
                                    "Instale o Termux primeiro.");
                        }
                    } catch (Exception e) {
                        updateStatus("❌ Erro ao abrir Termux: " + e.getMessage());
                    }
                });
            }).start();
        } else {
            requestPermissions();
        }
    }

    private void copyFilesToTermux() throws Exception {
        // Sempre copia para o diretório do app primeiro (backup)
        File storageDir = new File(getExternalFilesDir(null), "servidorzinho");
        storageDir.mkdirs();
        copyAssetsToDir(storageDir);

        // Tenta copiar para Termux e configurar auto-inicialização
        File termuxHome = new File("/data/data/com.termux/files/home");
        if (termuxHome.exists()) {
            try {
                File termuxDir = new File(termuxHome, "servidorzinho");
                termuxDir.mkdirs();
                copyAssetsToDir(termuxDir);

                // Cria script de inicialização automática no Termux
                setupTermuxAutoStart(termuxHome);

                handler.post(() -> {
                    updateStatus("✅ Arquivos copiados!\n\n" +
                            "Configurando instalação automática...");
                });
                return;
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Erro ao copiar para Termux: " + e.getMessage());
            }
        }

        // Se não conseguir acessar diretamente, usa método alternativo
        handler.post(() -> {
            updateStatus("⚠️ Não foi possível acessar Termux diretamente.\n\n" +
                    "Por favor, abra o Termux uma vez e feche.\n" +
                    "Depois tente instalar novamente.");
        });
    }

    private void setupTermuxAutoStart(File termuxHome) throws Exception {
        // Cria script que será executado automaticamente quando Termux abrir
        File bashrc = new File(termuxHome, ".bashrc");
        String autoInstallScript = "\n# MRIT Server Local - Auto Install\n" +
                "if [ ! -f ~/servidorzinho/.installed ] && [ -d ~/servidorzinho ]; then\n" +
                "    cd ~/servidorzinho\n" +
                "    bash INSTALAR_AUTO.sh > ~/servidorzinho/install.log 2>&1\n" +
                "    touch ~/servidorzinho/.installed\n" +
                "    cd ~/servidorzinho && bash iniciar_auto.sh > /dev/null 2>&1 &\n" +
                "fi\n" +
                "# Auto-start server if not running\n" +
                "if [ -d ~/servidorzinho ] && [ -f ~/servidorzinho/.installed ]; then\n" +
                "    if [ ! -f ~/servidorzinho/servidor.pid ] || ! ps -p $(cat ~/servidorzinho/servidor.pid) > /dev/null 2>&1; then\n"
                +
                "        cd ~/servidorzinho && bash iniciar_auto.sh > /dev/null 2>&1 &\n" +
                "    fi\n" +
                "fi\n";

        // Adiciona ao .bashrc se ainda não estiver lá
        String bashrcContent = "";
        if (bashrc.exists()) {
            java.io.FileInputStream fis = new java.io.FileInputStream(bashrc);
            byte[] data = new byte[(int) bashrc.length()];
            fis.read(data);
            fis.close();
            bashrcContent = new String(data);
        }

        if (!bashrcContent.contains("MRIT Server Local")) {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(bashrc, true);
            fos.write(autoInstallScript.getBytes());
            fos.close();
        }
    }

    private void copyAssetsToDir(File targetDir) throws Exception {
        String[] files = {
                "servidor_auto.py",
                "iniciar_auto.sh",
                "parar.sh",
                "INSTALAR_AUTO.sh",
                "setup_boot.sh",
                "requirements.txt"
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
