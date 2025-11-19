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
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(Looper.getMainLooper());
        statusText = findViewById(R.id.statusText);
        installButton = findViewById(R.id.installButton);

        installButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                startInstallation();
            } else {
                requestPermissions();
            }
        });

        checkStatus();
    }

    private boolean checkPermissions() {
        // Android 11+ não precisa de WRITE_EXTERNAL_STORAGE para arquivos próprios do app
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
            getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
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
                    updateStatus("✅ Instalação concluída!\n\n" +
                            "O servidor está pronto.\n" +
                            "Abra o Termux para iniciar o servidor.");
                    installButton.setText("Reinstalar");
                    installButton.setEnabled(true);

                    // Inicia o serviço em background
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

    private void copyFilesToTermux() throws Exception {
        // Sempre copia para o diretório do app primeiro (mais confiável)
        File storageDir = new File(getExternalFilesDir(null), "servidorzinho");
        storageDir.mkdirs();
        copyAssetsToDir(storageDir);
        
        // Tenta copiar para Termux se possível
        File termuxHome = new File("/data/data/com.termux/files/home");
        if (termuxHome.exists() && termuxHome.canWrite()) {
            try {
                File termuxDir = new File(termuxHome, "servidorzinho");
                termuxDir.mkdirs();
                copyAssetsToDir(termuxDir);
                handler.post(() -> {
                    updateStatus("✅ Arquivos copiados para Termux!\n\n" +
                            "Agora abra o Termux e execute:\n" +
                            "cd ~/servidorzinho\n" +
                            "bash INSTALAR_AUTO.sh");
                });
                return;
            } catch (Exception e) {
                // Se falhar, usa o fallback
            }
        }
        
        // Fallback: instrui usuário a copiar manualmente
        handler.post(() -> {
            updateStatus("✅ Arquivos copiados!\n\n" +
                    "Agora abra o Termux e execute:\n\n" +
                    "mkdir -p ~/servidorzinho\n" +
                    "cp -r " + storageDir.getAbsolutePath() + "/* ~/servidorzinho/\n" +
                    "cd ~/servidorzinho\n" +
                    "bash INSTALAR_AUTO.sh");
        });
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
        // Cria script de instalação que será executado via Termux
        // Usa Intent para abrir Termux com comando
        String installCommand = "cd ~/servidorzinho && " +
                "pkg update -y && " +
                "pkg install -y python && " +
                "pip install tinytuya && " +
                "bash INSTALAR_AUTO.sh";

        // Salva comando em arquivo temporário
        File cmdFile = new File(getCacheDir(), "install.sh");
        FileOutputStream fos = new FileOutputStream(cmdFile);
        fos.write(installCommand.getBytes());
        fos.close();

        // Abre Termux (usuário precisa executar manualmente na primeira vez)
        handler.post(() -> {
            updateStatus("✅ Arquivos copiados!\n\n" +
                    "Agora abra o Termux e execute:\n" +
                    "cd ~/servidorzinho\n" +
                    "bash INSTALAR_AUTO.sh\n\n" +
                    "Ou use o comando 'servidor-auto' depois.");
        });
    }

    private void setupAutoStart() throws Exception {
        // Configuração será feita pelo INSTALAR_AUTO.sh
        // Não precisa fazer nada aqui
    }

    private void startServerService() {
        Intent serviceIntent = new Intent(this, ServerService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
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
