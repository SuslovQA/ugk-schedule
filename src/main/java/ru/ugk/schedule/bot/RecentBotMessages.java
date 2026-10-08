package ru.ugk.schedule.bot;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/** Expiring history; eviction affects message cleanup only, never preferences. */
public final class RecentBotMessages<K, V> {
    private record History<V>(long updated, LinkedHashSet<V> ids) {}
    private final LinkedHashMap<K, History<V>> histories=new LinkedHashMap<>();
    private final Clock clock;
    private final int maxUsers, maxMessages;
    private final long ttlMillis;
    public RecentBotMessages() { this(Clock.systemUTC(),10000,32,Duration.ofHours(1)); }
    public RecentBotMessages(Clock clock,int maxUsers,int maxMessages,Duration ttl) {
        if (maxUsers < 1 || maxMessages < 1 || ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException();
        this.clock=clock; this.maxUsers=maxUsers; this.maxMessages=maxMessages; this.ttlMillis=ttl.toMillis();
    }
    public synchronized void remember(K user,V id) {
        expire();
        History<V> previous=histories.remove(user);
        LinkedHashSet<V> ids=previous == null ? new LinkedHashSet<>() : previous.ids();
        ids.add(id);
        while (ids.size() > maxMessages) ids.remove(ids.iterator().next());
        histories.put(user,new History<>(clock.millis(),ids));
        while (histories.size() > maxUsers) histories.remove(histories.keySet().iterator().next());
    }
    public synchronized Set<V> remove(K user) {
        expire(); History<V> history=histories.remove(user);
        return history == null ? Set.of() : new LinkedHashSet<>(history.ids());
    }
    public synchronized void forget(K user,V id) {
        expire(); History<V> history=histories.get(user);
        if (history == null) return;
        history.ids().remove(id);
        if (history.ids().isEmpty()) histories.remove(user);
    }
    public synchronized void expire() {
        long cutoff=clock.millis()-ttlMillis;
        histories.entrySet().removeIf(e -> e.getValue().updated() <= cutoff);
    }
}
