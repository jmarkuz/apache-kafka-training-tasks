# kafka-producer-wikimedia

Reads the Wikimedia Recent Changes stream and publishes every event to the `wikimedia.recentchange` topic.

## Topic configuration

The topic is declared in [`KafkaTopicConfig`](src/main/java/edu/jmarkuz/training/topic/KafkaTopicConfig.java) and is created
(or its partition count increased) by Spring's `KafkaAdmin` on startup. Values come from `application.yaml`:

| Property | Value | Why |
|---|---|---|
| `spring.kafka.topic.partitions` | `3` | Partitions are the unit of parallelism: a consumer group can have at most one active consumer per partition, so 3 partitions allow up to 3 parallel consumers (see roadmap 1.5). |
| `spring.kafka.topic.replicas` | `1` | A single local broker can hold only one replica of a partition. Use `3` on a multi-broker cluster (roadmap 4.2). |
| `spring.kafka.topic.retention-ms` | `604800000` | `retention.ms` = 7 days. Old log segments are deleted after that time. `retention.bytes` is left at its default (unlimited), so only time limits the topic. |
| `cleanup.policy` | `delete` | Messages are removed by age, not compacted (compaction is roadmap 4.3). |

Partitions can be increased later but never decreased, and increasing them changes which partition a given message key hashes to,
so choose the number up front for keyed topics (roadmap 1.2).

## Inspecting the topic

With the Conduktor Platform compose file running (broker on `localhost:9092`):

```bash
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic wikimedia.recentchange
```

(With a local Kafka distribution use `bin/kafka-topics.sh` instead of `kafka-topics`.) Output for this topic:

```
Topic: wikimedia.recentchange  TopicId: 0x8GsbToTCq4SxZz0NCI5Q  PartitionCount: 3  ReplicationFactor: 1  Configs: cleanup.policy=delete,segment.bytes=1073741824,retention.ms=604800000
	Topic: wikimedia.recentchange  Partition: 0  Leader: 1  Replicas: 1  Isr: 1  Elr:   LastKnownElr:
	Topic: wikimedia.recentchange  Partition: 1  Leader: 1  Replicas: 1  Isr: 1  Elr:   LastKnownElr:
	Topic: wikimedia.recentchange  Partition: 2  Leader: 1  Replicas: 1  Isr: 1  Elr:   LastKnownElr:
```

How to read it:

- **First line (topic level):** `PartitionCount: 3` and `ReplicationFactor: 1` are the values from `application.yaml`.
  `Configs` lists only settings that differ from the broker defaults: `cleanup.policy` and `retention.ms` are ours,
  `segment.bytes` (1 GiB, the size at which a log segment is rolled) is added by the broker.
- **One line per partition:**
  - `Partition` is the partition number (0-2).
  - `Leader` is the id of the broker that serves all reads and writes for the partition. Here every partition is led by broker `1`
    because there is only one broker.
  - `Replicas` lists the brokers that hold a copy (leader included). With replication factor 1 it is just the leader.
  - `Isr` (in-sync replicas) is the subset of replicas that are fully caught up with the leader. If `Isr` is smaller than `Replicas`,
    a replica is lagging or its broker is down. Only in-sync replicas can become the new leader, and `acks=all` waits for them
    (roadmap 1.3 and 4.2).
  - `Elr` / `LastKnownElr` (eligible leader replicas) only appear in newer Kafka versions and are empty for a healthy topic.

Other useful commands:

```bash
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
docker exec kafka kafka-configs --bootstrap-server localhost:9092 --describe --entity-type topics --entity-name wikimedia.recentchange
# change retention without redeploying (value in ms; here 1 day)
docker exec kafka kafka-configs --bootstrap-server localhost:9092 --alter --entity-type topics --entity-name wikimedia.recentchange --add-config retention.ms=86400000
```

Note: `KafkaAdmin` re-applies the partition count only; changing `retention-ms` in `application.yaml` for an existing topic does not
update it, use `kafka-configs` as above.

The settings are verified against a real broker by `KafkaProducerWikimediaApplicationTests`.

## Message keys and partitioning

Every event is sent with the **website it belongs to (`server_name`, e.g. `en.wikipedia.org`) as the message key**
(see `WikimediaChangesHandler.extractKey`). If the event cannot be parsed or has no `server_name`, it is sent without a key.

Why the key matters:

- Kafka picks the partition from the key: `partition = hash(key) mod number of partitions` (murmur2 hash in the default partitioner).
  The same key therefore always lands in the same partition.
- Ordering is guaranteed **only inside one partition**. Because all changes of one website share a partition, a consumer sees them
  in the order they were sent. Across different keys (partitions) there is no ordering.
- Without a key, messages are spread over partitions (sticky/round-robin), which balances load but gives no ordering per website.
- If the partition count changes, `hash(key) mod n` changes too, so existing keys may move to another partition and per-key
  ordering across the old and new data is lost. Choose the number of partitions up front for keyed topics.

### Custom partitioner

[`WikimediaPartitioner`](src/main/java/edu/jmarkuz/training/partitioner/WikimediaPartitioner.java) solves the **hot partition** problem:
a few websites (`www.wikidata.org`, `commons.wikimedia.org`) produce far more changes than the rest, so with the default partitioner
the partition they hash to is overloaded and shares its load with unrelated keys.

- Keys listed in `wikimedia.partitioner.hot-keys` are **isolated on partition 0**.
- All other keys are spread over partitions `1..n-1` with the same murmur2 hash, so a key still always maps to one partition
  and per-key ordering is preserved.
- Messages without a key are spread round-robin over partitions `1..n-1`; with a single partition everything goes to partition 0.

It is enabled in `application.yaml`:

```yaml
spring.kafka.producer.properties:
  partitioner.class: edu.jmarkuz.training.partitioner.WikimediaPartitioner
  wikimedia.partitioner.hot-keys: "www.wikidata.org,commons.wikimedia.org"
```

Remove `partitioner.class` to go back to Kafka's default partitioner. The trade-off of this design: partition 0 can only be as
fast as one consumer, and partitions `1..n-1` carry all the remaining keys, so it only helps if the hot keys really dominate the traffic.

### Seeing it in practice

Show key and partition of each message while the producer runs:

```bash
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic wikimedia.recentchange \
  --from-beginning --property print.key=true --property print.partition=true --property print.offset=true
```

Every `server_name` always shows the same partition number and growing offsets; `www.wikidata.org` and `commons.wikimedia.org`
only appear on partition 0.

Covered by tests: `WikimediaPartitionerTest` (unit), `WikimediaChangesHandlerKeyTest` (key extraction) and
`KafkaProducerWikimediaApplicationTests` (on a real broker: events with one key are consumed from a single partition in the order
they were sent, and hot keys are only on partition 0).

