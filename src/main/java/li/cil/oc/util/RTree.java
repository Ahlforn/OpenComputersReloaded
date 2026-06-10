package li.cil.oc.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A simple R-tree for spatial range queries, used for wireless network range lookups. Faithful port
 * of the Scala {@code RTree}. {@code Data} values map to a 3D point via the supplied
 * {@link Coordinate} function; points are stored as zero-volume leaf rectangles.
 *
 * <p>All public operations are synchronized on the tree instance, matching the original.
 */
public final class RTree<Data> {

    /** Maps a value to its {@code [x, y, z]} position. */
    @FunctionalInterface
    public interface Coordinate<T> {
        double[] apply(T value);
    }

    private final int maxEntries;
    private final int minEntries;
    private final Coordinate<Data> coordinate;

    /** Used for quick checks whether values are in the tree, e.g. for updates. */
    private final Map<Data, Leaf> entries = new HashMap<>();

    private NonLeaf root = new NonLeaf();

    public RTree(int maxEntries, Coordinate<Data> coordinate) {
        if (maxEntries < 2) throw new IllegalArgumentException("maxEntries must be larger or equal to 2.");
        this.maxEntries = maxEntries;
        this.minEntries = Math.max(maxEntries / 2, 1);
        this.coordinate = coordinate;
    }

    /** Returns the stored position {@code [x, y, z]} of a value, or {@code null} if not present. */
    public synchronized double[] position(Data value) {
        Leaf leaf = entries.get(value);
        return leaf == null ? null : new double[]{leaf.bounds.min.x, leaf.bounds.min.y, leaf.bounds.min.z};
    }

    public synchronized boolean add(Data value) {
        boolean replaced = remove(value);
        Leaf entry = new Leaf(value, new Point(coordinate.apply(value)));
        entries.put(value, entry);
        Node newNode = root.add(entry);
        if (newNode != root) {
            root = new NonLeaf(newNode, root);
        }
        return !replaced;
    }

    public synchronized boolean remove(Data value) {
        Leaf node = entries.remove(value);
        if (node == null) return false;
        root.remove(node);
        Node first = root.children.isEmpty() ? null : root.children.iterator().next();
        if (root.children.size() == 1 && first instanceof NonLeaf nonLeaf) {
            root = nonLeaf;
        } else {
            root.bounds = around(root.children);
        }
        return true;
    }

    public synchronized List<Data> query(double[] from, double[] to) {
        List<Data> result = new ArrayList<>();
        root.query(new Rectangle(new Point(from), new Point(to)), result);
        return result;
    }

    // ----------------------------------------------------------------------- //

    private Rectangle around(Collection<Node> values) {
        Point min = Point.POSITIVE_INFINITY;
        Point max = Point.NEGATIVE_INFINITY;
        for (Node value : values) {
            min = value.bounds.min.min(min);
            max = value.bounds.max.max(max);
        }
        return new Rectangle(min, max);
    }

    // ----------------------------------------------------------------------- //

    private abstract class Node {
        Rectangle bounds;

        boolean isLeaf() {
            return true;
        }

        /** Insert and return the (possibly new sibling) node that should replace this in the parent. */
        abstract Node add(Node value);

        /** Returns the node signalling the structural change, or {@code null} if nothing happened. */
        abstract Node remove(Node value);

        abstract void query(Rectangle query, List<Data> result);
    }

    private final class NonLeaf extends Node {
        final Set<Node> children = new LinkedHashSet<>();

        NonLeaf() {
            bounds = new Rectangle(Point.POSITIVE_INFINITY, Point.NEGATIVE_INFINITY);
        }

        @SafeVarargs
        NonLeaf(Node... nodes) {
            this();
            for (Node child : nodes) {
                children.add(child);
                bounds = bounds.including(child.bounds);
            }
        }

        @Override
        boolean isLeaf() {
            return !children.isEmpty() && children.iterator().next() instanceof Leaf;
        }

        @Override
        Node add(Node value) {
            uncheckedAdd(value);
            if (children.size() > maxEntries) {
                return split();
            }
            bounds = bounds.including(value.bounds);
            return this;
        }

        private void uncheckedAdd(Node value) {
            Node bestChild = null;
            double bestGrowth = Double.POSITIVE_INFINITY;
            double bestVolume = Double.POSITIVE_INFINITY;
            for (Node child : children) {
                if (child.isLeaf() && !(value instanceof Leaf)) continue;
                double oldVolume = child.bounds.volume();
                double volume = child.bounds.including(value.bounds).volume();
                double growth = volume - oldVolume;
                if (growth < bestGrowth || (growth == bestGrowth && volume < bestVolume)) {
                    bestChild = child;
                    bestGrowth = growth;
                    bestVolume = volume;
                }
            }
            if (bestChild != null) {
                children.add(bestChild.add(value));
            } else {
                // Empty root, or re-inserting children of a removed child node.
                children.add(value);
            }
        }

        @Override
        Node remove(Node value) {
            if (bounds.intersects(value.bounds)) {
                for (Node child : new ArrayList<>(children)) {
                    Node change = child.remove(value);
                    if (change == null) continue;
                    if (change == child) {
                        // Underflow after removing node, or child was the node to remove.
                        children.remove(child);
                        if (child instanceof NonLeaf node) {
                            for (Node grandChild : node.children) uncheckedAdd(grandChild);
                            if (children.size() > maxEntries) {
                                return split(); // Escalate overflow.
                            }
                        }
                        if (children.size() < minEntries) {
                            return this; // Escalate underflow.
                        }
                        bounds = around(children);
                        return value;
                    } else if (change == value) {
                        bounds = around(children);
                        return value;
                    } else {
                        // Overflow due to split after underflow.
                        uncheckedAdd(change);
                        if (children.size() > maxEntries) {
                            return split(); // Escalate overflow.
                        }
                        bounds = around(children);
                        return value;
                    }
                }
            }
            return null;
        }

        @Override
        void query(Rectangle query, List<Data> result) {
            if (query.intersects(bounds)) {
                for (Node child : children) child.query(query, result);
            }
        }

        private Node split() {
            List<Node> values = new ArrayList<>(children);
            Node seed1 = null;
            Node seed2 = null;
            double worst = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < values.size(); i++) {
                Node si = values.get(i);
                for (int j = i + 1; j < values.size(); j++) {
                    Node sj = values.get(j);
                    double d = si.bounds.including(sj.bounds).volume() - si.bounds.volume() - sj.bounds.volume();
                    if (d > worst) {
                        seed1 = si;
                        seed2 = sj;
                        worst = d;
                    }
                }
            }
            if (seed1 == null || seed2 == null) throw new AssertionError();

            SplitResult r1 = new SplitResult(seed1.bounds);
            r1.set.add(seed1);
            SplitResult r2 = new SplitResult(seed2.bounds);
            r2.set.add(seed2);

            Set<Node> list = new LinkedHashSet<>(values);
            list.remove(seed1);
            list.remove(seed2);
            while (!list.isEmpty()) {
                if (minEntries - r1.set.size() >= list.size()) {
                    for (Node value : list) r1.add(value);
                    list.clear();
                } else if (minEntries - r2.set.size() >= list.size()) {
                    for (Node value : list) r2.add(value);
                    list.clear();
                } else {
                    Node bestValue = null;
                    SplitResult r = r1;
                    double best = Double.NEGATIVE_INFINITY;
                    for (Node value : list) {
                        double newVol1 = r1.volumeIncluding(value);
                        double newVol2 = r2.volumeIncluding(value);
                        double growth1 = newVol1 - r1.volume();
                        double growth2 = newVol2 - r2.volume();
                        double d = Math.abs(growth2 - growth1);
                        if (d > best) {
                            bestValue = value;
                            r = (growth1 < growth2 || (growth1 == growth2 && newVol1 < newVol2)) ? r1 : r2;
                            best = d;
                        }
                    }
                    if (bestValue == null) throw new AssertionError();
                    list.remove(bestValue);
                    r.add(bestValue);
                }
            }

            children.clear();
            children.addAll(r1.set);
            bounds = r1.bounds;

            NonLeaf ll = new NonLeaf();
            ll.children.addAll(r2.set);
            ll.bounds = r2.bounds;
            return ll;
        }
    }

    private final class Leaf extends Node {
        final Data data;

        Leaf(Data data, Point point) {
            this.data = data;
            this.bounds = new Rectangle(point, point);
        }

        @Override
        Node add(Node value) {
            return value;
        }

        @Override
        Node remove(Node value) {
            return value == this ? this : null;
        }

        @Override
        void query(Rectangle query, List<Data> result) {
            if (query.intersects(bounds)) result.add(data);
        }
    }

    private final class SplitResult {
        final Set<Node> set = new LinkedHashSet<>();
        Rectangle bounds;

        SplitResult(Rectangle bounds) {
            this.bounds = bounds;
        }

        void add(Node value) {
            set.add(value);
            bounds = bounds.including(value.bounds);
        }

        double volume() {
            return bounds.volume();
        }

        double volumeIncluding(Node value) {
            return bounds.including(value.bounds).volume();
        }
    }

    // ----------------------------------------------------------------------- //

    private static final class Point {
        static final Point NEGATIVE_INFINITY = new Point(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        static final Point POSITIVE_INFINITY = new Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

        final double x;
        final double y;
        final double z;

        Point(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Point(double[] p) {
            this(p[0], p[1], p[2]);
        }

        Point min(Point other) {
            return new Point(Math.min(x, other.x), Math.min(y, other.y), Math.min(z, other.z));
        }

        Point max(Point other) {
            return new Point(Math.max(x, other.x), Math.max(y, other.y), Math.max(z, other.z));
        }
    }

    private static final class Rectangle {
        final Point min;
        final Point max;

        Rectangle(Point min, Point max) {
            this.min = min;
            this.max = max;
        }

        Rectangle including(Rectangle value) {
            return new Rectangle(value.min.min(min), value.max.max(max));
        }

        boolean intersects(Rectangle value) {
            return value.min.x <= max.x && value.min.y <= max.y && value.min.z <= max.z
                    && value.max.x >= min.x && value.max.y >= min.y && value.max.z >= min.z;
        }

        double volume() {
            return (max.x - min.x) * (max.y - min.y) * (max.z - min.z);
        }
    }
}
