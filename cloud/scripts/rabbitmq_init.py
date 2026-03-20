#!/usr/bin/env python3
"""
rabbitmq_init.py — Initialize RabbitMQ Exchanges, Queues, and Bindings
=======================================================================
Run once after RabbitMQ container starts. Creates the complete
messaging topology for SudarshanChakra.

Usage:
  docker exec -it rabbitmq bash
  python3 /app/rabbitmq_init.py

Or run from host (AMQP port 5672, not MQTT 1883):
  pip install pika
  RABBITMQ_PASS=yourpass RABBITMQ_HOST=127.0.0.1 python3 rabbitmq_init.py

Env: RABBITMQ_HOST (default 127.0.0.1), RABBITMQ_PORT (default 5672),
     RABBITMQ_USER, RABBITMQ_PASS

If Management UI says plugins need a restart: docker restart rabbitmq && sleep 30
"""

import os
import sys

import pika

# AMQP (used by pika / Java services) — NOT MQTT. Default port is 5672.
# Do NOT point this at 1883 (MQTT) or you get StreamLostError / IncompatibleProtocolError.
RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "127.0.0.1")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "admin")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "changeme")


def main():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    params = pika.ConnectionParameters(
        host=RABBITMQ_HOST,
        port=RABBITMQ_PORT,
        credentials=credentials,
        connection_attempts=5,
        retry_delay=2,
        socket_timeout=10,
        heartbeat=600,
        blocked_connection_timeout=300,
    )
    try:
        connection = pika.BlockingConnection(params)
    except Exception as e:
        print(
            f"ERROR: Cannot open AMQP connection to {RABBITMQ_HOST}:{RABBITMQ_PORT} — {e}\n"
            "\n"
            "  rabbitmq_init.py uses AMQP (pika), port 5672 by default.\n"
            "  Port 1883 is MQTT only; connecting there causes EOF / protocol errors.\n"
            "\n"
            "  If you changed plugins in the Management UI, restart the broker:\n"
            "    docker restart rabbitmq\n"
            "  Then wait ~30s and run this script again.\n"
            "\n"
            "  Check:  docker exec rabbitmq rabbitmq-diagnostics -q ping\n"
            "          echo $RABBITMQ_PASS   (must match RABBITMQ_DEFAULT_PASS)\n",
            file=sys.stderr,
        )
        raise SystemExit(1) from e
    channel = connection.channel()

    print("=== Creating Exchanges ===")

    # Topic exchange for alerts (edge → cloud)
    channel.exchange_declare(
        exchange="farm.alerts",
        exchange_type="topic",
        durable=True,
    )
    print("  ✓ farm.alerts (topic)")

    # Direct exchange for commands (cloud → edge)
    channel.exchange_declare(
        exchange="farm.commands",
        exchange_type="direct",
        durable=True,
    )
    print("  ✓ farm.commands (direct)")

    # Topic exchange for events (status, heartbeats, suppression logs)
    channel.exchange_declare(
        exchange="farm.events",
        exchange_type="topic",
        durable=True,
    )
    print("  ✓ farm.events (topic)")

    # Dead-letter exchange for failed messages
    channel.exchange_declare(
        exchange="farm.dead-letter",
        exchange_type="fanout",
        durable=True,
    )
    print("  ✓ farm.dead-letter (fanout)")

    channel.exchange_declare(
        exchange="farm.water",
        exchange_type="topic",
        durable=True,
    )
    print("  ✓ farm.water (topic)")

    print("\n=== Creating Queues ===")

    dead_letter_args = {
        "x-dead-letter-exchange": "farm.dead-letter",
    }

    # Alert queues — priority-based routing
    channel.queue_declare(
        queue="alert.critical",
        durable=True,
        arguments={
            **dead_letter_args,
            "x-max-priority": 10,  # Priority queue
            "x-message-ttl": 86400000,  # 24h TTL
        },
    )
    print("  ✓ alert.critical (priority queue)")

    channel.queue_declare(
        queue="alert.high",
        durable=True,
        arguments={
            **dead_letter_args,
            "x-message-ttl": 86400000,
        },
    )
    print("  ✓ alert.high")

    channel.queue_declare(
        queue="alert.warning",
        durable=True,
        arguments={
            **dead_letter_args,
            "x-message-ttl": 43200000,  # 12h TTL
        },
    )
    print("  ✓ alert.warning")

    # Siren command queue
    channel.queue_declare(
        queue="siren.commands",
        durable=True,
        arguments=dead_letter_args,
    )
    print("  ✓ siren.commands")

    # Node events queue (heartbeats, online/offline)
    channel.queue_declare(
        queue="node.events",
        durable=True,
        arguments={
            "x-message-ttl": 3600000,  # 1h TTL
        },
    )
    print("  ✓ node.events")

    # Worker suppression log queue
    channel.queue_declare(
        queue="worker.suppression",
        durable=True,
        arguments={
            "x-message-ttl": 86400000,
        },
    )
    print("  ✓ worker.suppression")

    channel.queue_declare(
        queue="water.level",
        durable=True,
        arguments=dead_letter_args,
    )
    print("  ✓ water.level")

    channel.queue_declare(
        queue="water.status",
        durable=True,
        arguments=dead_letter_args,
    )
    print("  ✓ water.status")

    # Dead-letter queue (catch-all for failed messages)
    channel.queue_declare(
        queue="dead-letter-sink",
        durable=True,
    )
    print("  ✓ dead-letter-sink")

    print("\n=== Creating Bindings ===")

    # Alert bindings — route by priority
    channel.queue_bind(queue="alert.critical", exchange="farm.alerts",
                       routing_key="farm.alerts.critical")
    print("  ✓ farm.alerts.critical → alert.critical")

    channel.queue_bind(queue="alert.high", exchange="farm.alerts",
                       routing_key="farm.alerts.high")
    print("  ✓ farm.alerts.high → alert.high")

    channel.queue_bind(queue="alert.warning", exchange="farm.alerts",
                       routing_key="farm.alerts.warning")
    print("  ✓ farm.alerts.warning → alert.warning")

    # Siren command binding
    channel.queue_bind(queue="siren.commands", exchange="farm.commands",
                       routing_key="farm.siren.trigger")
    channel.queue_bind(queue="siren.commands", exchange="farm.commands",
                       routing_key="farm.siren.stop")
    print("  ✓ farm.siren.* → siren.commands")

    # Node event bindings
    channel.queue_bind(queue="node.events", exchange="farm.events",
                       routing_key="node.*")
    print("  ✓ node.* → node.events")

    # Worker suppression binding
    channel.queue_bind(queue="worker.suppression", exchange="farm.events",
                       routing_key="farm.events.worker_identified")
    print("  ✓ farm.events.worker_identified → worker.suppression")

    channel.queue_bind(queue="water.level", exchange="farm.water", routing_key="water.level")
    channel.queue_bind(queue="water.status", exchange="farm.water", routing_key="water.status")
    print("  ✓ farm.water.* → water.level / water.status")

    # Dead-letter binding
    channel.queue_bind(queue="dead-letter-sink", exchange="farm.dead-letter",
                       routing_key="")
    print("  ✓ * → dead-letter-sink")

    print("\n=== Creating Users ===")
    # Note: User creation via rabbitmqctl is more reliable than pika
    # Run these inside the RabbitMQ container:
    print("""
  Run inside rabbitmq container:
    rabbitmqctl add_user edge-publisher <password>
    rabbitmqctl set_permissions edge-publisher "^$" "farm\\..*" "^$"
    
    rabbitmqctl add_user backend-consumer <password>
    rabbitmqctl set_permissions backend-consumer ".*" ".*" ".*"
    
    rabbitmqctl add_user android-client <password>
    rabbitmqctl set_permissions android-client "^$" "farm\\.siren\\..*" "farm\\.alerts\\..*"
    """)

    connection.close()
    print("\n=== RabbitMQ initialization complete ===")


if __name__ == "__main__":
    main()
