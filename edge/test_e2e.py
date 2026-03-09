#!/usr/bin/env python3
"""
test_e2e.py — End-to-End Flow Test for Dev Mode
=================================================
Connects to the local MQTT broker and validates that the entire
detection → zone → fusion → alert → siren chain works.

Usage:
  # Start dev stack first:
  cd edge && docker compose -f docker-compose.dev.yml up -d

  # Then run tests:
  python test_e2e.py                          # All tests
  python test_e2e.py --test alert_flow        # Single test
  python test_e2e.py --broker 192.168.1.100   # Remote broker

Tests:
  1. alert_flow:     Wait for mock detection → alert appears on MQTT
  2. worker_suppress: Toggle worker OFF → intruder alert fires
  3. fall_detect:    Simulate fall → CRITICAL alert on farm/alerts/critical
  4. siren_trigger:  Send siren command → ack received
  5. siren_stop:     Send siren stop → ack received
  6. full:           Run all tests in sequence
"""

import argparse
import json
import sys
import time
import threading
import paho.mqtt.client as mqtt

BROKER = "localhost"
PORT = 1883
TIMEOUT = 30

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
RESET = "\033[0m"
BOLD = "\033[1m"

received_messages = {}
lock = threading.Lock()


def on_message(client, userdata, msg):
    with lock:
        topic = msg.topic
        if topic not in received_messages:
            received_messages[topic] = []
        try:
            payload = json.loads(msg.payload.decode())
        except Exception:
            payload = msg.payload.decode()
        received_messages[topic].append({"payload": payload, "time": time.time()})


def wait_for_topic(topic_prefix, timeout=TIMEOUT):
    """Wait until a message arrives on a topic matching the prefix."""
    start = time.time()
    while time.time() - start < timeout:
        with lock:
            for topic, msgs in received_messages.items():
                if topic.startswith(topic_prefix) and msgs:
                    return msgs[-1]
        time.sleep(0.5)
    return None


def clear_messages():
    with lock:
        received_messages.clear()


def test_alert_flow(client):
    """Test: Mock detection → zone check → alert published."""
    print(f"\n{BOLD}TEST 1: Alert Flow{RESET}")
    print("  Waiting for mock detection to produce an alert...")
    print("  (Mock pipeline generates detections every 5 seconds)")

    clear_messages()
    result = wait_for_topic("farm/alerts/", timeout=60)

    if result:
        p = result["payload"]
        print(f"  {GREEN}✓ Alert received!{RESET}")
        print(f"    Priority:  {p.get('priority', '?')}")
        print(f"    Class:     {p.get('detection_class', '?')}")
        print(f"    Zone:      {p.get('zone_name', '?')}")
        print(f"    Camera:    {p.get('camera_id', '?')}")
        print(f"    Node:      {p.get('node_id', '?')}")
        print(f"    Mock:      {p.get('mock', '?')}")
        return True
    else:
        print(f"  {RED}✗ No alert received within 60s{RESET}")
        print("    Check: is edge-ai-dev container running?")
        print("    Check: does zones.json have zones matching mock detection bboxes?")
        return False


def test_worker_suppression(client):
    """Test: Disable worker tag → intruder alert should fire."""
    print(f"\n{BOLD}TEST 2: Worker Suppression{RESET}")
    print("  Step 1: Disabling mock worker presence...")

    client.publish("dev/simulate/worker_toggle",
                   json.dumps({"present": False}), qos=1)
    time.sleep(2)

    print("  Step 2: Waiting for person intruder alert (worker tag absent)...")
    print("          (cam-01 person aligns every ~200s in the mock cycle; patience needed)")
    clear_messages()

    # The mock pipeline cycles 10 scenarios across 8 cameras (5s each).
    # cam-01 + person scenario aligns at index 0, 40, 80... = every 200s.
    # We need enough time for at least one full cycle after toggle.
    start = time.time()
    timeout = 250
    found = False
    while time.time() - start < timeout:
        with lock:
            for topic, msgs in received_messages.items():
                if topic.startswith("farm/alerts/"):
                    for msg in msgs:
                        p = msg["payload"]
                        if isinstance(p, dict) and p.get("detection_class") == "person" \
                                and not p.get("worker_suppressed"):
                            found = True
                            break
                if found:
                    break
        if found:
            elapsed = time.time() - start
            print(f"  {GREEN}✓ Intruder alert fired (worker absent) after {elapsed:.0f}s!{RESET}")
            client.publish("dev/simulate/worker_toggle",
                           json.dumps({"present": True}), qos=1)
            return True
        time.sleep(1)

    client.publish("dev/simulate/worker_toggle",
                   json.dumps({"present": True}), qos=1)

    print(f"  {RED}✗ No person intruder alert received within {timeout}s{RESET}")
    return False


def test_fall_detection(client):
    """Test: Simulate fall → CRITICAL alert."""
    print(f"\n{BOLD}TEST 3: Fall Detection (Child Safety){RESET}")
    print("  Simulating ESP32 fall event for TAG-C001...")

    clear_messages()
    client.publish("dev/simulate/fall",
                   json.dumps({"tag_id": "TAG-C001"}), qos=1)

    result = wait_for_topic("farm/alerts/critical", timeout=15)

    if result:
        p = result["payload"]
        if p.get("detection_class") == "fall_detected":
            print(f"  {GREEN}✓ CRITICAL fall alert received!{RESET}")
            print(f"    Zone:    {p.get('zone_name', '?')}")
            print(f"    Source:  {p.get('metadata', {}).get('source', '?')}")
            print(f"    Tag:     {p.get('metadata', {}).get('tag_id', '?')}")
            return True

    print(f"  {RED}✗ No fall alert received within 15s{RESET}")
    return False


def test_siren_trigger(client):
    """Test: Send siren trigger → ack received."""
    print(f"\n{BOLD}TEST 4: Siren Trigger{RESET}")
    print("  Sending siren trigger command...")

    clear_messages()
    client.publish("farm/siren/trigger", json.dumps({
        "command": "trigger",
        "siren_url": "http://example.com/siren.mp3",
        "triggered_by": "test_e2e",
    }), qos=1)

    result = wait_for_topic("farm/siren/ack", timeout=10)

    if result:
        p = result["payload"]
        if p.get("status") == "siren_activated":
            print(f"  {GREEN}✓ Siren triggered and acknowledged!{RESET}")
            print(f"    Node:  {p.get('node_id', '?')}")
            print(f"    Mock:  {p.get('mock', '?')}")
            return True

    print(f"  {RED}✗ No siren ack received{RESET}")
    return False


def test_siren_stop(client):
    """Test: Send siren stop → ack received."""
    print(f"\n{BOLD}TEST 5: Siren Stop{RESET}")
    print("  Sending siren stop command...")

    clear_messages()
    client.publish("farm/siren/stop", json.dumps({
        "command": "stop",
    }), qos=1)

    result = wait_for_topic("farm/siren/ack", timeout=10)

    if result:
        p = result["payload"]
        if p.get("status") == "siren_stopped":
            print(f"  {GREEN}✓ Siren stopped and acknowledged!{RESET}")
            return True

    print(f"  {RED}✗ No siren stop ack received{RESET}")
    return False


TESTS = {
    "alert_flow": test_alert_flow,
    "worker_suppress": test_worker_suppression,
    "fall_detect": test_fall_detection,
    "siren_trigger": test_siren_trigger,
    "siren_stop": test_siren_stop,
}


def main():
    parser = argparse.ArgumentParser(description="SudarshanChakra E2E Test Suite")
    parser.add_argument("--broker", default=BROKER, help="MQTT broker host")
    parser.add_argument("--port", type=int, default=PORT, help="MQTT broker port")
    parser.add_argument("--test", default="full",
                        choices=list(TESTS.keys()) + ["full"],
                        help="Which test to run")
    args = parser.parse_args()

    print(f"{BOLD}{'=' * 60}")
    print("  SudarshanChakra — End-to-End Test Suite (Dev Mode)")
    print(f"  Broker: {args.broker}:{args.port}")
    print(f"{'=' * 60}{RESET}")

    client = mqtt.Client(client_id="e2e-test-runner")
    client.on_message = on_message

    try:
        client.connect(args.broker, args.port, keepalive=60)
    except Exception:
        print(f"\n{RED}Cannot connect to MQTT broker at {args.broker}:{args.port}{RESET}")
        print("Start the dev stack first: docker compose -f docker-compose.dev.yml up")
        sys.exit(1)

    client.subscribe("farm/#", qos=1)
    client.subscribe("dev/#", qos=1)
    client.loop_start()

    time.sleep(1)  # Let subscriptions settle

    try:
        if args.test == "full":
            tests_to_run = list(TESTS.items())
        else:
            tests_to_run = [(args.test, TESTS[args.test])]

        results = {}
        for name, test_fn in tests_to_run:
            try:
                results[name] = test_fn(client)
            except Exception as e:
                print(f"  {RED}✗ Test crashed: {e}{RESET}")
                results[name] = False

        # Summary
        print(f"\n{BOLD}{'=' * 60}")
        print("  RESULTS")
        print(f"{'=' * 60}{RESET}")

        passed = sum(1 for v in results.values() if v)
        total = len(results)

        for name, result in results.items():
            icon = f"{GREEN}PASS{RESET}" if result else f"{RED}FAIL{RESET}"
            print(f"  {icon}  {name}")

        print(f"\n  {passed}/{total} tests passed")
    finally:
        client.loop_stop()
        client.disconnect()

    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
