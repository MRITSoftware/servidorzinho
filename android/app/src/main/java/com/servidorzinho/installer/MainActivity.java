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
        // Abre Play Store para instalar Termux
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://details?id=com.termux"));
        startActivity(intent);
    }

    private void openTermuxAndInstall() {
        if (checkPermissions()) {
            new Thread(() -> {
                try {
                    // Garante que arquivos foram copiados
                    handler.post(() -> updateStatus("Preparando arquivos..."));
                    copyFilesToTermux();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Erro: " + e.getMessage());
                }
                
                // Abre o Termux com comando para executar o script
                handler.post(() -> {
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            
                            // Tenta passar comando via Intent (se Termux API estiver instalado)
                            File downloadsDir = new File(getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS), "MRIT_Server");
                            String scriptPath = new File(downloadsDir, "copy_to_termux.sh").getAbsolutePath();
                            
                            startActivity(intent);
                            updateStatus("✅ Termux aberto!\n\n" +
                                    "Execute no Termux:\n" +
                                    "bash " + scriptPath + "\n\n" +
                                    "Ou copie manualmente:\n" +
                                    "cp -r /sdcard/Download/MRIT_Server/* ~/servidorzinho/");
                        } else {
                            installTermux();
                            updateStatus("⚠️ Termux não encontrado.\n\nInstale o Termux primeiro.");
                        }
                    } catch (Exception e) {
                        updateStatus("❌ Erro: " + e.getMessage());
                    }
                });
            }).start();
        } else {
            requestPermissions();
        }
    }

    private void copyFilesToTermux() throws Exception {
        // Copia para storage compartilhado (acessível pelo Termux)
        File downloadsDir = new File(getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "MRIT_Server");
        downloadsDir.mkdirs();
        copyAssetsToDir(downloadsDir);
        
        // Também copia para diretório do app (backup)
        File storageDir = new File(getExternalFilesDir(null), "servidorzinho");
        storageDir.mkdirs();
        copyAssetsToDir(storageDir);

        // Cria script que o Termux pode executar para copiar automaticamente
        createTermuxCopyScript(downloadsDir);
        
        handler.post(() -> {
            updateStatus("✅ Arquivos copiados para Downloads/MRIT_Server!\n\n" +
                    "Clique em 'Abrir Termux e Instalar' para continuar.");
        });
    }
    
    private File getExternalStoragePublicDirectory(String type) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return new File(getExternalFilesDir(null).getParentFile().getParentFile(), 
                "Android/media/" + getPackageName() + "/" + type);
        } else {
            return android.os.Environment.getExternalStoragePublicDirectory(type);
        }
    }
    
    private void createTermuxCopyScript(File sourceDir) throws Exception {
        // Cria script que o Termux executa automaticamente
        File scriptFile = new File(sourceDir, "copy_to_termux.sh");
        String scriptContent = 
            "#!/bin/bash\n" +
            "# Script para copiar arquivos para Termux\n" +
            "mkdir -p ~/servidorzinho\n" +
            "cp -r " + sourceDir.getAbsolutePath() + "/* ~/servidorzinho/ 2>/dev/null || true\n" +
            "chmod +x ~/servidorzinho/*.sh 2>/dev/null || true\n" +
            "cd ~/servidorzinho\n" +
            "if [ ! -f .installed ]; then\n" +
            "    bash INSTALAR_AUTO.sh > install.log 2>&1\n" +
            "    touch .installed\n" +
            "    bash iniciar_auto.sh > /dev/null 2>&1 &\n" +
            "fi\n" +
            "# Auto-start server if not running\n" +
            "if [ ! -f servidor.pid ] || ! ps -p $(cat servidor.pid) > /dev/null 2>&1; then\n" +
            "    bash iniciar_auto.sh > /dev/null 2>&1 &\n" +
            "fi\n";
        
        FileOutputStream fos = new FileOutputStream(scriptFile);
        fos.write(scriptContent.getBytes());
        fos.close();
        scriptFile.setExecutable(true);
    }

    private void setupTermuxAutoStart() throws Exception {
        // Tenta adicionar script ao .bashrc do Termux via storage compartilhado
        File downloadsDir = new File(getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "MRIT_Server");
        File bashrcScript = new File(downloadsDir, "setup_bashrc.sh");
        
        String bashrcContent = 
            "#!/bin/bash\n" +
            "# Adiciona auto-start ao .bashrc do Termux\n" +
            "if ! grep -q 'MRIT Server Local' ~/.bashrc 2>/dev/null; then\n" +
            "    echo '' >> ~/.bashrc\n" +
            "    echo '# MRIT Server Local - Auto Install' >> ~/.bashrc\n" +
            "    echo 'if [ -d ~/servidorzinho ] && [ ! -f ~/servidorzinho/.installed ]; then' >> ~/.bashrc\n" +
            "    echo '    cd ~/servidorzinho' >> ~/.bashrc\n" +
            "    echo '    bash INSTALAR_AUTO.sh > install.log 2>&1' >> ~/.bashrc\n" +
            "    echo '    touch .installed' >> ~/.bashrc\n" +
            "    echo '    bash iniciar_auto.sh > /dev/null 2>&1 &' >> ~/.bashrc\n" +
            "    echo 'fi' >> ~/.bashrc\n" +
            "    echo '# Auto-start server if not running' >> ~/.bashrc\n" +
            "    echo 'if [ -d ~/servidorzinho ] && [ -f ~/servidorzinho/.installed ]; then' >> ~/.bashrc\n" +
            "    echo '    if [ ! -f ~/servidorzinho/servidor.pid ] || ! ps -p \\$(cat ~/servidorzinho/servidor.pid) > /dev/null 2>&1; then' >> ~/.bashrc\n" +
            "    echo '        cd ~/servidorzinho && bash iniciar_auto.sh > /dev/null 2>&1 &' >> ~/.bashrc\n" +
            "    echo '    fi' >> ~/.bashrc\n" +
            "    echo 'fi' >> ~/.bashrc\n" +
            "fi\n";
        
        FileOutputStream fos = new FileOutputStream(bashrcScript);
        fos.write(bashrcContent.getBytes());
        fos.close();
        bashrcScript.setExecutable(true);
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
