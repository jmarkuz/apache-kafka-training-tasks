package edu.jmarkuz.training.partitioner;

import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class WikimediaPartitionerTest {

    private static final String TOPIC = "wikimedia.recentchange";

    private final WikimediaPartitioner partitioner = new WikimediaPartitioner();

    WikimediaPartitionerTest() {
        partitioner.configure(Map.of(WikimediaPartitioner.HOT_KEYS_CONFIG, " www.wikidata.org , commons.wikimedia.org "));
    }

    @Test
    void sameKeyAlwaysGoesToSamePartition() {
        Cluster cluster = cluster(3);

        int first = partition("en.wikipedia.org", cluster);
        for (int i = 0; i < 100; i++) {
            assertThat(partition("en.wikipedia.org", cluster)).isEqualTo(first);
        }
    }

    @Test
    void hotKeysAreIsolatedOnPartitionZero() {
        Cluster cluster = cluster(3);

        assertThat(partition("www.wikidata.org", cluster)).isZero();
        assertThat(partition("commons.wikimedia.org", cluster)).isZero();
    }

    @Test
    void otherKeysNeverUsePartitionZero() {
        Cluster cluster = cluster(3);

        Set<Integer> used = new HashSet<>();
        IntStream.range(0, 200).forEach(i -> used.add(partition("site-" + i + ".example.org", cluster)));

        assertThat(used).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void messagesWithoutKeyAreSpreadOverNonHotPartitions() {
        Cluster cluster = cluster(4);

        Set<Integer> used = new HashSet<>();
        IntStream.range(0, 30).forEach(i -> used.add(partitioner.partition(TOPIC, null, null, "v", null, cluster)));

        assertThat(used).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void usesPartitionZeroWhenTopicHasSinglePartition() {
        Cluster cluster = cluster(1);

        assertThat(partition("www.wikidata.org", cluster)).isZero();
        assertThat(partition("en.wikipedia.org", cluster)).isZero();
    }

    private int partition(String key, Cluster cluster) {
        return partitioner.partition(TOPIC, key, key.getBytes(StandardCharsets.UTF_8), "v", null, cluster);
    }

    private static Cluster cluster(int partitions) {
        Node node = new Node(1, "localhost", 9092);
        List<PartitionInfo> infos = IntStream.range(0, partitions)
                .mapToObj(p -> new PartitionInfo(TOPIC, p, node, new Node[]{node}, new Node[]{node}))
                .toList();
        return new Cluster("test", List.of(node), infos, Collections.emptySet(), Collections.emptySet());
    }
}
