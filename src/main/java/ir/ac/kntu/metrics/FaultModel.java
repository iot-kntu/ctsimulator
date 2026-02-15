package ir.ac.kntu.metrics;

import ir.ac.kntu.concurrenttransmission.CtNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class FaultModel {

    private final double packetLossProb;
    private final double silentNodeRatio;
    private final double faultyNodeRatio;
    private final int delayJitterSlots;
    private final long seed;
    private final Set<Integer> silentNodes;
    private final Set<Integer> faultyNodes;
    private final Random runtimeRandom;

    public FaultModel(double packetLossProb,
                      double silentNodeRatio,
                      double faultyNodeRatio,
                      int delayJitterSlots,
                      long seed) {
        this(packetLossProb, silentNodeRatio, faultyNodeRatio, delayJitterSlots, seed,
                Collections.emptySet(), Collections.emptySet(), new Random(seed ^ 0x9E3779B97F4A7C15L));
    }

    private FaultModel(double packetLossProb,
                       double silentNodeRatio,
                       double faultyNodeRatio,
                       int delayJitterSlots,
                       long seed,
                       Set<Integer> silentNodes,
                       Set<Integer> faultyNodes,
                       Random runtimeRandom) {
        this.packetLossProb = packetLossProb;
        this.silentNodeRatio = silentNodeRatio;
        this.faultyNodeRatio = faultyNodeRatio;
        this.delayJitterSlots = delayJitterSlots;
        this.seed = seed;
        this.silentNodes = silentNodes;
        this.faultyNodes = faultyNodes;
        this.runtimeRandom = runtimeRandom;
    }

    public FaultModel withNodes(Collection<CtNode> nodes) {
        List<Integer> ids = new ArrayList<>();
        if (nodes != null) {
            for (CtNode node : nodes) {
                if (node != null && node.getId() >= 0) {
                    ids.add(node.getId());
                }
            }
        }
        Collections.sort(ids);

        int nodeCount = ids.size();
        int silentCount = (int) Math.round(silentNodeRatio * nodeCount);
        int faultyCount = (int) Math.round(faultyNodeRatio * nodeCount);
        if (silentCount + faultyCount > nodeCount) {
            faultyCount = Math.max(0, nodeCount - silentCount);
        }

        Random assignRandom = new Random(seed);
        Collections.shuffle(ids, assignRandom);

        Set<Integer> silent = new HashSet<>();
        Set<Integer> faulty = new HashSet<>();

        for (int i = 0; i < silentCount && i < ids.size(); i++) {
            silent.add(ids.get(i));
        }
        for (int i = silentCount; i < silentCount + faultyCount && i < ids.size(); i++) {
            faulty.add(ids.get(i));
        }

        return new FaultModel(packetLossProb, silentNodeRatio, faultyNodeRatio, delayJitterSlots, seed,
                Collections.unmodifiableSet(silent), Collections.unmodifiableSet(faulty),
                new Random(seed ^ 0xC3A5C85C97CB3127L));
    }

    public boolean isSilent(int nodeId) {
        return silentNodes.contains(nodeId);
    }

    public boolean isFaulty(int nodeId) {
        return faultyNodes.contains(nodeId);
    }

    public boolean shouldDropPacket() {
        if (packetLossProb <= 0) {
            return false;
        }
        return runtimeRandom.nextDouble() < packetLossProb;
    }

    public int sampleJitterSlots() {
        if (delayJitterSlots <= 0) {
            return 0;
        }
        return runtimeRandom.nextInt(delayJitterSlots + 1);
    }

    public double packetLossProb() {
        return packetLossProb;
    }

    public double silentNodeRatio() {
        return silentNodeRatio;
    }

    public double faultyNodeRatio() {
        return faultyNodeRatio;
    }

    public int delayJitterSlots() {
        return delayJitterSlots;
    }

    public long seed() {
        return seed;
    }

    public Set<Integer> silentNodes() {
        return silentNodes;
    }

    public Set<Integer> faultyNodes() {
        return faultyNodes;
    }

    public Random runtimeRandom() {
        return runtimeRandom;
    }
}
