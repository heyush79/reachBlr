package in.commutemap.routing;

import java.util.Arrays;

/**
 * A binary min-heap of (cost, node) pairs in two primitive arrays, so the search allocates no objects
 * per step. The push and pop steps are the same as the prototype's heap, so equal costs come out in the
 * same order (that decides which of two equally fast trips is shown).
 */
final class MinHeap {

    private double[] cost = new double[1024];
    private int[] node = new int[1024];
    private int size;

    int size() {
        return size;
    }

    double topCost() {
        return cost[0];
    }

    int topNode() {
        return node[0];
    }

    void push(double c, int n) {
        if (size == cost.length) {
            cost = Arrays.copyOf(cost, size * 2);
            node = Arrays.copyOf(node, size * 2);
        }
        cost[size] = c;
        node[size] = n;
        int i = size++;
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (cost[p] <= cost[i]) break;
            swap(p, i);
            i = p;
        }
    }

    /** Removes the top; read it first with {@link #topCost()} and {@link #topNode()}. */
    void pop() {
        size--;
        if (size == 0) return;
        cost[0] = cost[size];
        node[0] = node[size];
        int i = 0;
        for (;;) {
            int l = 2 * i + 1, r = l + 1, m = i;
            if (l < size && cost[l] < cost[m]) m = l;
            if (r < size && cost[r] < cost[m]) m = r;
            if (m == i) break;
            swap(m, i);
            i = m;
        }
    }

    private void swap(int a, int b) {
        double c = cost[a];
        cost[a] = cost[b];
        cost[b] = c;
        int n = node[a];
        node[a] = node[b];
        node[b] = n;
    }
}
