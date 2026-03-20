# 🚀 START HERE - Super Simple Guide

## What You Need to Know (30 seconds)

1. **INFRA** = Database + Message Bus (start this FIRST)
2. **BACKEND** = 5 Java programs (start this SECOND)
3. **EDGE** = Camera watcher Python program (start this LAST)

---

## 🎯 Just Run This One Command

```bash
./scripts/deploy_local.sh
```

**That's it!** It does everything automatically.

---

## OR Do It Step by Step (If You Want Control)

### Step 1: Start Database + Message Bus
```bash
./setup_and_build_all.sh deploy-infra
```
**Wait 30 seconds** for it to finish.

---

### Step 2: Start Backend Services
```bash
./setup_and_build_all.sh deploy-backend
```
**Wait 1-2 minutes** for all 5 services to start.

---

### Step 3: Register Your Camera
```bash
./scripts/register_camera.sh cam-tapo-c110-01 "Test Camera" \
  "rtsp://administrator:interOP@123@192.168.68.56:554/stream2" \
  edge-node-local "TP-Link Tapo C110"
```

---

### Step 4: Start Edge (Camera Watcher)
```bash
cd edge
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

export NODE_ID=edge-node-local
export VPN_BROKER_IP=localhost
export MQTT_USER=admin
export MQTT_PASS=devpassword123

python3 farm_edge_node.py
```

---

## ✅ Check if It Works

Open 3 terminals and run:

**Terminal 1:**
```bash
docker ps
# Should show: postgres and rabbitmq
```

**Terminal 2:**
```bash
curl http://localhost:8080/actuator/health
# Should show: {"status":"UP"}
```

**Terminal 3:**
```bash
curl http://localhost:5000/health
# Should show: OK
```

---

## 📖 More Details

If you want to understand what each part does, read:
- `docs/SIMPLE_EXPLANATION.md` - Plain English explanation
- `docs/QUICK_START_LOCAL.md` - Detailed steps

---

## 🆘 If Something Breaks

1. **Check Docker is running:** `docker ps`
2. **Check Java 21:** `java -version`
3. **Check Python 3:** `python3 --version`
4. **Check camera is reachable:** `ping 192.168.68.56`

That's all you need to know! 🎉
