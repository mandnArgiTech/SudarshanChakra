#!/usr/bin/env python3
"""
rabbitmq_init.py — Initialize RabbitMQ Exchanges, Queues, and Bindings
=======================================================================
Run once after RabbitMQ container starts. Creates the complete
messaging topology for SudarshanChakra.

Usage:
  docker exec -it rabbitmq bash
  python3 /app/rabbitmq_init.py

Or run from VPS host:
  pip install pika
  python3 rabbitmq_init.py
"""

import os
import pika

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "admin")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "changeme")


def main():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    connection = pika.BlockingConnection(
        pika.ConnectionParameters(host=RABBITMQ_HOST, credentials=credentials)
    )
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
