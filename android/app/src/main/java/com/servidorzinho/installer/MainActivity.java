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
            openTermux();
        });

        copyInstallButton.setOnClickListener(v -> {
            // Tenta múltiplos caminhos possíveis
            String installCmd = "if [ -f ~/storage/downloads/MRIT_Server/copy_to_termux.sh ]; then\n" +
                    "  bash ~/storage/downloads/MRIT_Server/copy_to_termux.sh\n" +
                    "elif [ -f ~/servidorzinho/INSTALAR_AUTO.sh ]; then\n" +
                    "  cd ~/servidorzinho && bash INSTALAR_AUTO.sh\n" +
                    "else\n" +
                    "  echo '❌ Arquivos não encontrados! Execute o app novamente.'\n" +
                    "fi";
            copyToClipboard(installCmd);
            updateStatus("✅ Comando de instalação copiado!\n\n" +
                    "📱 No Termux:\n" +
                    "1. Cole o comando\n" +
                    "2. Pressione Enter\n" +
                    "3. Aguarde a instalação (5-15 min)");
        });

        copyStartButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && bash iniciar_auto.sh");
            updateStatus("✅ Comando copiado!\n\n" +
                    "📱 No Termux:\n" +
                    "1. Cole o comando\n" +
                    "2. Pressione Enter\n" +
                    "3. O servidor iniciará em background");
        });

        copyStatusButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && bash testar_servidor.sh");
            updateStatus("✅ Comando copiado!\n\n" +
                    "📱 No Termux:\n" +
                    "1. Cole o comando\n" +
                    "2. Pressione Enter\n" +
                    "3. Verá o status completo do servidor");
        });

        copyLogsButton.setOnClickListener(v -> {
            copyToClipboard("cd ~/servidorzinho && tail -30 servidor.log");
            updateStatus("✅ Comando copiado!\n\n" +
                    "📱 No Termux:\n" +
                    "1. Cole o comando\n" +
                    "2. Pressione Enter\n" +
                    "3. Verá os últimos 30 logs");
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

    private void openTermux() {
        // Tenta abrir o Termux diretamente
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (intent != null) {
                startActivity(intent);
                updateStatus("✅ Termux aberto!\n\n" +
                        "📋 Agora você pode:\n" +
                        "1. Usar os botões abaixo para copiar comandos\n" +
                        "2. Colar no Termux e executar\n\n" +
                        "💡 Dica: Cole com Ctrl+Shift+V ou long press");
            } else {
                updateStatus("❌ Termux não encontrado!\n\n" +
                        "Por favor, instale o Termux da Play Store primeiro.");
                installTermux();
            }
        } catch (Exception e) {
            updateStatus("❌ Erro ao abrir Termux: " + e.getMessage());
        }
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
        // Para Android 11+ (API 30+), usa scoped storage
        // Tenta múltiplos locais para compatibilidade
        File[] targetDirs;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ - usa apenas diretório do app (mais confiável)
            targetDirs = new File[] {
                    new File(getExternalFilesDir(null), "servidorzinho")
            };
        } else {
            // Android 10 e anteriores - tenta múltiplos locais
            targetDirs = new File[] {
                    new File("/sdcard/Download/MRIT_Server"),
                    new File("/storage/emulated/0/Download/MRIT_Server"),
                    new File(getExternalFilesDir(null), "servidorzinho")
            };
        }

        boolean copied = false;
        String lastErrorMessage = null;
        File copiedDir = null;

        for (File dir : targetDirs) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                if (!dir.exists() || !dir.canWrite()) {
                    throw new Exception("Não foi possível criar ou escrever em: " + dir.getAbsolutePath());
                }
                copyAssetsToDir(dir);
                createTermuxCopyScript(dir);
                createTermuxAutoRunScript(dir);
                copied = true;
                copiedDir = dir;
                android.util.Log.d("MainActivity", "Arquivos copiados com sucesso para: " + dir.getAbsolutePath());
                break; // Se conseguiu copiar, não precisa tentar outros
            } catch (Exception e) {
                lastErrorMessage = e.getMessage();
                android.util.Log.e("MainActivity", "Erro ao copiar para " + dir + ": " + e.getMessage());
            }
        }

        if (!copied) {
            final String errorMsg = lastErrorMessage != null ? lastErrorMessage : "Erro desconhecido";
            handler.post(() -> {
                updateStatus("❌ Erro ao copiar arquivos.\n\n" +
                        "Erro: " + errorMsg + "\n\n" +
                        "Verifique as permissões do app nas configurações.");
            });
            return;
        }

        // Copia comando para área de transferência
        final File finalDir = copiedDir;
        String installCommand = "if [ -f \"" + finalDir.getAbsolutePath() + "/copy_to_termux.sh\" ]; then\n" +
                "  bash \"" + finalDir.getAbsolutePath() + "/copy_to_termux.sh\"\n" +
                "elif [ -f ~/storage/downloads/MRIT_Server/copy_to_termux.sh ]; then\n" +
                "  bash ~/storage/downloads/MRIT_Server/copy_to_termux.sh\n" +
                "elif [ -f ~/servidorzinho/INSTALAR_AUTO.sh ]; then\n" +
                "  cd ~/servidorzinho && bash INSTALAR_AUTO.sh && bash iniciar_auto.sh\n" +
                "else\n" +
                "  echo '❌ Arquivos não encontrados! Execute o app novamente.'\n" +
                "fi";

        copyToClipboard(installCommand);

        handler.post(() -> {
            updateStatus("✅ Arquivos copiados!\n\n" +
                    "📱 O Termux será aberto automaticamente.\n\n" +
                    "📋 Instruções:\n" +
                    "1. O comando já foi copiado para área de transferência\n" +
                    "2. No Termux, cole o comando (Ctrl+Shift+V ou long press)\n" +
                    "3. Pressione Enter\n" +
                    "4. Aguarde a instalação (10-15 minutos)\n\n" +
                    "💡 Dica: Não feche o Termux durante a instalação!");
        });

        // Abre o Termux automaticamente após 1 segundo
        handler.postDelayed(() -> {
            openTermux();
        }, 1000);
    }

    private void createTermuxCopyScript(File sourceDir) throws Exception {
        // Cria script que usa diretório atual (mais confiável)
        File scriptFile = new File(sourceDir, "copy_to_termux.sh");
        String scriptContent = "#!/bin/bash\n" +
                "set -e\n" +
                "clear\n" +
                "echo '╔══════════════════════════════════════╗'\n" +
                "echo '║   MRIT Server Local - Instalação    ║'\n" +
                "echo '╚══════════════════════════════════════╝'\n" +
                "echo ''\n" +
                "echo '📦 Passo 1/4: Copiando arquivos...'\n" +
                "mkdir -p ~/servidorzinho\n" +
                "SOURCE_DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"\n" +
                "cp -r \"$SOURCE_DIR\"/* ~/servidorzinho/ 2>/dev/null || true\n" +
                "rm ~/servidorzinho/copy_to_termux.sh 2>/dev/null || true\n" +
                "chmod +x ~/servidorzinho/*.sh 2>/dev/null || true\n" +
                "chmod +x ~/servidorzinho/*.py 2>/dev/null || true\n" +
                "echo '✅ Arquivos copiados!'\n" +
                "echo ''\n" +
                "echo '📦 Passo 2/4: Configurando inicialização automática...'\n" +
                "cd ~/servidorzinho\n" +
                "# Configura auto-run no .bashrc se ainda não estiver configurado\n" +
                "if [ -f auto_run.sh ] && ! grep -q 'servidorzinho/auto_run.sh' ~/.bashrc 2>/dev/null; then\n" +
                "    echo '' >> ~/.bashrc\n" +
                "    echo '# Auto-inicia servidorzinho' >> ~/.bashrc\n" +
                "    echo 'if [ -f ~/servidorzinho/auto_run.sh ]; then' >> ~/.bashrc\n" +
                "    echo '    bash ~/servidorzinho/auto_run.sh' >> ~/.bashrc\n" +
                "    echo 'fi' >> ~/.bashrc\n" +
                "    echo '✅ Configuração automática ativada!'\n" +
                "else\n" +
                "    echo '✅ Já configurado'\n" +
                "fi\n" +
                "echo ''\n" +
                "cd ~/servidorzinho\n" +
                "if [ ! -f .installed ]; then\n" +
                "    echo '📦 Passo 3/4: Instalando dependências...'\n" +
                "    echo '⏳ Isso pode demorar 10-15 minutos'\n" +
                "    echo '⏳ Por favor, NÃO feche o Termux'\n" +
                "    echo ''\n" +
                "    bash INSTALAR_AUTO.sh || {\n" +
                "        echo ''\n" +
                "        echo '❌ Erro na instalação!'\n" +
                "        echo 'Tente executar: bash INSTALAR_AUTO.sh'\n" +
                "        exit 1\n" +
                "    }\n" +
                "    echo ''\n" +
                "    echo '📦 Passo 4/4: Iniciando servidor...'\n" +
                "    bash iniciar_auto.sh\n" +
                "else\n" +
                "    echo '📦 Passo 3/4: Verificando dependências...'\n" +
                "    if ! python3 -c 'import tinytuya' 2>/dev/null; then\n" +
                "        echo '⚠️  Dependências faltando. Reinstalando...'\n" +
                "        bash INSTALAR_AUTO.sh\n" +
                "    fi\n" +
                "    echo '📦 Passo 4/4: Iniciando servidor...'\n" +
                "    bash iniciar_auto.sh\n" +
                "fi\n" +
                "echo ''\n" +
                "echo '✅ Instalação completa!'\n" +
                "echo ''\n" +
                "echo '📌 Comandos úteis:'\n" +
                "echo '   start   -> Inicia servidor'\n" +
                "echo '   status  -> Verifica se está rodando'\n" +
                "echo '   logs    -> Mostra logs'\n" +
                "echo '   stop    -> Para servidor'\n" +
                "echo ''\n" +
                "echo '💡 O servidor iniciará automaticamente quando você abrir o Termux!'";

        FileOutputStream fos = new FileOutputStream(scriptFile);
        fos.write(scriptContent.getBytes());
        fos.close();
        scriptFile.setExecutable(true);
    }

    private void createTermuxAutoRunScript(File sourceDir) throws Exception {
        // Cria script que será executado automaticamente quando Termux abrir
        // Este script será copiado para ~/.bashrc ou ~/.termux/boot
        File scriptFile = new File(sourceDir, "auto_run.sh");
        String scriptContent = "#!/bin/bash\n" +
                "# Script de inicialização automática\n" +
                "# Este script será executado quando o Termux abrir\n" +
                "\n" +
                "# Aguarda um pouco para garantir que tudo está pronto\n" +
                "sleep 2\n" +
                "\n" +
                "# Verifica se já foi executado (evita loops)\n" +
                "if [ -f ~/.servidorzinho_autorun_done ]; then\n" +
                "    exit 0\n" +
                "fi\n" +
                "\n" +
                "# Marca como executado\n" +
                "touch ~/.servidorzinho_autorun_done\n" +
                "\n" +
                "# Verifica se os arquivos foram copiados\n" +
                "if [ -f ~/servidorzinho/INSTALAR_AUTO.sh ]; then\n" +
                "    cd ~/servidorzinho\n" +
                "    \n" +
                "    # Se não está instalado, instala\n" +
                "    if [ ! -f .installed ]; then\n" +
                "        echo '🚀 Iniciando instalação automática...'\n" +
                "        bash INSTALAR_AUTO.sh && bash iniciar_auto.sh\n" +
                "    else\n" +
                "        # Verifica se dependências estão OK\n" +
                "        if ! python3 -c 'import tinytuya' 2>/dev/null; then\n" +
                "            echo '⚠️  Reinstalando dependências...'\n" +
                "            bash INSTALAR_AUTO.sh\n" +
                "        fi\n" +
                "        \n" +
                "        # Inicia servidor se não estiver rodando\n" +
                "        if [ ! -f servidor.pid ] || ! ps -p $(cat servidor.pid) > /dev/null 2>&1; then\n" +
                "            echo '🚀 Iniciando servidor...'\n" +
                "            bash iniciar_auto.sh\n" +
                "        fi\n" +
                "    fi\n" +
                "fi\n";

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
                "testar_servidor.sh",
                "auto_run.sh"
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
        Toast.makeText(this, "✅ Comando copiado para área de transferência!", Toast.LENGTH_LONG).show();
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
