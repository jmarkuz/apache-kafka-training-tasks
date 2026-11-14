package edu.jmarkuz.training.partitioner;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.utils.Utils;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Custom partitioner that isolates "hot" keys.
 * <p>
 * Some Wikimedia websites (for example wikidata or commons) produce far more changes than the others. With the default
 * partitioner such a key can overload the one partition it hashes to, together with whatever other keys share it.
 * This partitioner puts the configured hot keys alone on partition 0 and spreads every other key over partitions
 * {@code 1..n-1} using the same murmur2 hash as the default partitioner. A given key always maps to the same partition,
 * so per-key ordering is preserved. Messages without a key are spread round-robin over partitions {@code 1..n-1}.
 * <p>
 * Configure with {@code partitioner.class} and {@value #HOT_KEYS_CONFIG} (comma-separated list of keys).
 * With a single partition everything goes to partition 0.
 */
public class WikimediaPartitioner implements Partitioner {

    public static final String HOT_KEYS_CONFIG = "wikimedia.partitioner.hot-keys";

    private volatile Set<String> hotKeys = Set.of();
    private final AtomicInteger roundRobin = new AtomicInteger();

    @Override
    public void configure(Map<String, ?> configs) {
        Object value = configs.get(HOT_KEYS_CONFIG);
        if (value != null) {
            hotKeys = Arrays.stream(value.toString().split(","))
                    .map(String::trim)
                    .filter(key -> !key.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        int partitions = cluster.partitionCountForTopic(topic);
        if (partitions <= 1) {
            return 0;
        }
        if (keyBytes == null) {
            return 1 + Math.floorMod(roundRobin.getAndIncrement(), partitions - 1);
        }
        if (hotKeys.contains(String.valueOf(key))) {
            return 0;
        }
        return 1 + Utils.toPositive(Utils.murmur2(keyBytes)) % (partitions - 1);
    }

    @Override
    public void close() {
        // nothing to release
    }
}
