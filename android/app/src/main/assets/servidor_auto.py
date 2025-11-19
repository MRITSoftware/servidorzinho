#!/usr/bin/env python3
"""
Servidorzinho com auto-reinício e monitoramento de conexão
Roda em background e se reinicia automaticamente
"""
import os
import sys
import json
import time
import subprocess
import socket
from http.server import HTTPServer, BaseHTTPRequestHandler
import tinytuya

# =========================
# CONFIGURAÇÕES
# =========================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "local_config.json")
LOG_PATH = os.path.join(BASE_DIR, "servidor.log")
PID_PATH = os.path.join(BASE_DIR, "servidor.pid")

HTTP_PORT = 8080
CHECK_INTERVAL = 30  # Verifica conexão a cada 30 segundos
MAX_RETRIES = 3  # Tentativas antes de reiniciar

# =========================
# FUNÇÕES DE LOG
# =========================

def log(msg: str, to_file: bool = True):
    """Log para console e arquivo"""
    timestamp = time.strftime('%Y-%m-%d %H:%M:%S')
    log_msg = f"[{timestamp}] {msg}"
    print(log_msg, flush=True)
    if to_file:
        try:
            with open(LOG_PATH, "a", encoding="utf-8") as f:
                f.write(log_msg + "\n")
        except:
            pass

# =========================
# VERIFICAÇÃO DE CONEXÃO
# =========================

def check_internet(host="8.8.8.8", port=53, timeout=3):
    """Verifica se há conexão com internet"""
    try:
        socket.setdefaulttimeout(timeout)
        socket.socket(socket.AF_INET, socket.SOCK_STREAM).connect((host, port))
        return True
    except:
        return False

def check_local_network():
    """Verifica se há rede local ativa"""
    try:
        # Tenta fazer bind na porta (indica que a interface de rede está ativa)
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.bind(("0.0.0.0", 0))
        s.close()
        return True
    except:
        return False

# =========================
# CONFIGURAÇÃO
# =========================

def load_or_create_config():
    """Carrega ou cria config"""
    if not os.path.exists(CONFIG_PATH):
        # Tenta ler de variável de ambiente ou usa padrão
        site_name = os.environ.get("SITE_NAME", "Site Automático")
        
        data = {
            "site_name": site_name,
            "http_port": HTTP_PORT,
            "devices": {}
        }
        
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        
        log(f"✅ Config criado: {site_name}")
        return data
    
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return json.load(f)

config = load_or_create_config()

# =========================
# FUNÇÕES DO SERVIDOR
# =========================

def save_device(device_id: str, name: str, local_key: str, lan_ip: str, version: float):
    """Salva/atualiza um dispositivo na configuração."""
    try:
        if "devices" not in config:
            config["devices"] = {}
        
        config["devices"][device_id] = {
            "name": name,
            "local_key": local_key,
            "lan_ip": lan_ip,
            "version": version,
            "last_updated": time.strftime('%Y-%m-%d %H:%M:%S')
        }
        
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(config, f, indent=2)
        
        log(f"💾 Dispositivo salvo: {name} ({device_id})")
        return True
    except Exception as e:
        log(f"❌ Erro ao salvar dispositivo: {e}")
        return False

def send_tuya(device_id: str, action: str, local_key: str, lan_ip: str, 
              device_name: str, version: float = 3.3):
    """Envia comando para o dispositivo Tuya"""
    try:
        log(f"🔌 Enviando '{action}' para {device_name} @ {lan_ip}")
        
        d = tinytuya.OutletDevice(device_id, lan_ip, local_key)
        d.set_version(version)
        
        if action.lower() == "on":
            result = d.turn_on()
        else:
            result = d.turn_off()
        
        if isinstance(result, dict) and result.get("success"):
            return {"success": True, "message": f"Comando {action} enviado", "device_name": device_name}
        else:
            return {"success": False, "error": str(result)}
    except Exception as e:
        log(f"❌ Erro ao enviar comando: {e}")
        return {"success": False, "error": str(e)}

# =========================
# HTTP HANDLER
# =========================

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/status":
            data = {
                "status": "ok",
                "site_name": config.get("site_name"),
                "devices_count": len(config.get("devices", {})),
                "port": config.get("http_port", HTTP_PORT),
                "uptime": time.time() - start_time,
            }
            self.send_json(200, data)
        elif self.path == "/devices":
            devices = []
            for dev_id, info in config.get("devices", {}).items():
                devices.append({"tuya_device_id": dev_id, **info})
            self.send_json(200, devices)
        else:
            self.send_json(404, {"success": False, "error": "Rota não encontrada"})
    
    def do_POST(self):
        if self.path != "/command":
            self.send_json(404, {"success": False, "error": "Rota não encontrada"})
            return
        
        length = int(self.headers.get("Content-Length", 0))
        if length == 0:
            self.send_json(400, {"success": False, "error": "Body vazio"})
            return
        
        try:
            body = self.rfile.read(length).decode("utf-8")
            data = json.loads(body)
        except Exception as e:
            self.send_json(400, {"success": False, "error": f"JSON inválido: {e}"})
            return
        
        required = ["tuya_device_id", "action", "local_key", "lan_ip"]
        for r in required:
            if r not in data or data[r] in (None, "", []):
                self.send_json(400, {"success": False, "error": f"Campo faltando: {r}"})
                return
        
        device_id = data["tuya_device_id"]
        action = data["action"]
        local_key = data["local_key"]
        lan_ip = data["lan_ip"]
        device_name = data.get("device_name", "Dispositivo")
        version = float(data.get("version", 3.3))
        
        save_device(device_id, device_name, local_key, lan_ip, version)
        result = send_tuya(device_id, action, local_key, lan_ip, device_name, version)
        self.send_json(200, result)
    
    def send_json(self, code, data):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode("utf-8"))
    
    def log_message(self, format, *args):
        return

# =========================
# MONITORAMENTO EM BACKGROUND
# =========================

def monitor_connection():
    """Thread que monitora conexão e reinicia se necessário"""
    retry_count = 0
    
    while True:
        time.sleep(CHECK_INTERVAL)
        
        # Verifica rede local (mais importante que internet)
        if not check_local_network():
            retry_count += 1
            log(f"⚠️  Rede local indisponível (tentativa {retry_count}/{MAX_RETRIES})")
            
            if retry_count >= MAX_RETRIES:
                log("🔄 Reiniciando servidor devido à perda de rede...")
                restart_server()
                return
        else:
            retry_count = 0  # Reset contador se rede voltou

def restart_server():
    """Reinicia o servidor"""
    log("🔄 Reiniciando servidor...")
    python = sys.executable
    os.execl(python, python, *sys.argv)

# =========================
# MAIN
# =========================

start_time = time.time()

def main():
    # Salva PID
    with open(PID_PATH, "w") as f:
        f.write(str(os.getpid()))
    
    log("🚀 Servidorzinho Auto iniciado")
    log(f"📌 Site: {config.get('site_name')}")
    log(f"🌐 Porta: {HTTP_PORT}")
    
    # Inicia monitoramento em thread separada
    import threading
    monitor_thread = threading.Thread(target=monitor_connection, daemon=True)
    monitor_thread.start()
    
    # Inicia servidor HTTP
    try:
        server = HTTPServer(("0.0.0.0", HTTP_PORT), Handler)
        log("✅ Servidor HTTP ativo")
        log("📌 Rotas: GET /status, GET /devices, POST /command")
        server.serve_forever()
    except KeyboardInterrupt:
        log("⏹️  Servidor interrompido pelo usuário")
    except Exception as e:
        log(f"❌ Erro fatal: {e}")
        time.sleep(5)
        restart_server()
    finally:
        if os.path.exists(PID_PATH):
            os.remove(PID_PATH)

if __name__ == "__main__":
    main()

