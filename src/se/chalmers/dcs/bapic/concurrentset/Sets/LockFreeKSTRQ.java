package se.chalmers.dcs.bapic.concurrentset.Sets;
/**
 *  An implementation of a non-blocking k-ary search tree supporting range queries.
 *  Copyright (C) 2012 Trevor Brown
 *  Contact (me [at] tbrown [dot] pro) with any questions or comments.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.concurrent.atomic.*;

public class LockFreeKSTRQ<E extends Comparable<? super E>, V> {

    private static final AtomicReferenceFieldUpdater<Node, Info> infoUpdater =
            AtomicReferenceFieldUpdater.newUpdater(Node.class, Info.class, "info");
    private final Node<E,V> root;

    private final int K;

    public LockFreeKSTRQ(final int K) {
        this(K, new Node<E,V>(K, true));
    }

    private LockFreeKSTRQ(final int K, final Node root) {
        this.K = K;
        this.root = root;
    }
    /**
     * Determines whether a key is present in the tree.
     * @return true if the key is present in the tree, and false otherwise
     * @throws NullPointerException in the event that key is null
     */
    public final boolean containsKey(final E key) {
        if (key == null) throw new NullPointerException();
        Node<E,V> l = root.c.get(0);
        while (l.c != null) l = child(key, l);  /* while l is internal */
        return l.hasKey(key);
    }

    /**
     * Adds a key-value pair to the tree if the key does not already exist.
     * @return the previous value mapped to the key, or null in the event that
     *         (1) the key was not previously in the tree and the new
     *             value was successfully assigned, or
     *         (2) the key existed in the tree,
     *             and the value stored with it was null.
     * @throws NullPointerException in the event that key is null
     */
    public final boolean add(final E value){
        V added = putIfAbsent(value, (V) value);
        return added == null;
    }
    private final V putIfAbsent(final E key, final V value) {
        if (key == null) throw new NullPointerException();
        Node<E,V> p, l, newchild;
        Info<E,V> pinfo;
        int pindex; // index of the child of p that points to l

        while (true) {
            // search
            p = root;
            pinfo = p.info; // TODO: NOTE THIS LINE IS UNNECESSARY
            l = p.c.get(0);
            while (l.c != null) {
                p = l;
                l = child(key, l);
            }

            // - read pinfo once instead of every iteration of the previous loop
            // then re-read and verify the child pointer from p to l
            // (so it is as if p.info were read first)
            // and also store the index of the child pointer of p that points to l
            pinfo = p.info;
            Node<E,V> currentL = p.c.get(p.kcount);
            pindex = p.kcount;
            for (int i=0;i<p.kcount;i++) {
                if (less(key, (E)p.k[i])) {
                    currentL = p.c.get(i);
                    pindex = i;
                    break;
                }
            }

            if (l != currentL) continue;

            if (l.hasKey(key)) return l.getValue(key);
            else if (pinfo != null && pinfo.getClass() != Clean.class) help(pinfo);
            else {
                // SPROUTING INSERTION
                if (l.kcount == K-1) { // l is full of keys
                    // create internal node with 4 children sorted by key
                    newchild = new Node<E,V>(key, value, l);

                    // SIMPLE INSERTION
                } else {
                    // create leaf node with sorted keys
                    // (at least one key of l is null and, by structural invariant,
                    // nulls appear at the end, so the last key is null)
                    newchild = new Node<E,V>(key, value, l, false);
                }

                // flag and perform the insertion
                final IInfo<E,V> newPInfo = new IInfo<E,V>(l, p, newchild, pindex);
                if (infoUpdater.compareAndSet(p, pinfo, newPInfo)) {	    // [[ iflag CAS ]]
                    helpInsert(newPInfo);
                    return null;
                } else {
                    // help current operation first
                    help(p.info);
                }
            }
        }
    }

    /**
     * Remove a key from the tree.
     * @return the value that was removed from the tree, or null if the key was
     *         not in the tree (or if the value stored with it was null)
     * @throws NullPointerException in the event that key is null
     */
    public final boolean remove(final E value){
        V removed = removeIfPresent(value);
        return removed != null;
    }
    private final V removeIfPresent(final E key) {
        if (key == null) throw new NullPointerException();
        Node<E,V> gp, p, l, newchild;
        Info<E,V> gpinfo, pinfo;
        int pindex;  // index of the child of p that points to l
        int gpindex; // index of the child of gp that points to p

        while (true) {
            // search
            gp = null;
            gpinfo = null;
            p = root;
            pinfo = p.info;
            l = p.c.get(0);
            while (l.c != null) {
                gp = p;
                p = l;
                l = child(key, l);
            }

            // - read gpinfo once instead of every iteration of the previous loop
            // then re-read and verify the child pointer from gp to p
            // (so it is as if gp.info were read first)
            // and also store the index of the child pointer of gp that points to p
            gpinfo = gp.info;
            Node<E,V> currentP = gp.c.get(gp.kcount);
            gpindex = gp.kcount;
            for (int i=0;i<gp.kcount;i++) {
                if (less(key, (E)gp.k[i])) {
                    currentP = gp.c.get(i);
                    gpindex = i;
                    break;
                }
            }

            if (p != currentP) continue;

            // - then do the same for pinfo and the child pointer from p to l
            pinfo = p.info;
            Node<E,V> currentL = p.c.get(p.kcount);
            pindex = p.kcount;
            for (int i=0;i<p.kcount;i++) {
                if (less(key, (E)p.k[i])) {
                    currentL = p.c.get(i);
                    pindex = i;
                    break;
                }
            }
            if (l != currentL) continue;

            // if the key is not in the tree, return null
            if (!l.hasKey(key))
                return null;
            else if (gpinfo != null && gpinfo.getClass() != Clean.class)
                help(gpinfo);
            else if (pinfo != null && pinfo.getClass() != Clean.class)
                help(pinfo);
            else {
                // count number of non-empty children of p
                int ccount = 0;
                if (l.kcount == 1) {
                    for (int i=0;i<=p.kcount;i++) {
                        if (p.c.get(i).kcount > 0) {
                            ccount++;
                            if (ccount > 2) break; // [todo] add this optimization for large k
                        }
                    }
                }

                // PRUNING DELETION
                if (l.kcount == 1 && ccount == 2) {
                    final DInfo<E,V> newGPInfo = new DInfo<E,V>(l, p, gp, pinfo, gpindex);
                    if (infoUpdater.compareAndSet(gp, gpinfo, newGPInfo)) { // [[ dflag CAS ]]
                        if (helpDelete(newGPInfo)) return l.getValue(key);
                    } else {
                        help(gp.info);
                    }

                    // SIMPLE DELETION
                } else {
                    // create leaf with sorted keys
                    newchild = new Node<E,V>(key, l);

                    // flag and perform the key deletion (like insertion)
                    final IInfo<E,V> newPInfo = new IInfo<E,V>(l, p, newchild, pindex);
                    if (infoUpdater.compareAndSet(p, pinfo, newPInfo)) {	// [[ kdflag CAS ]]
                        helpInsert(newPInfo);
                        return l.getValue(key);
                    } else {
                        help(p.info);
                    }
                }
            }
        }
    }

    /**
     *
     * PRIVATE FUNCTIONS
     *
     */

    // Precondition: `nonnull' is non-null
    private static <E extends Comparable<? super E>, V> boolean equal(final E nonnull, final E other) {
        if (other == null) return false;
        return nonnull.compareTo(other) == 0;
    }

    // Precondition: `nonnull' is non-null
    private static <E extends Comparable<? super E>, V> boolean less(final E nonnull, final E other) {
        if (other == null) return true;
        return nonnull.compareTo(other) < 0;
    }

    // Precondition: `nonnull' is non-null
    private static <E extends Comparable<? super E>, V> boolean lessEqual(final E nonnull, final E other) {
        if (other == null) return true;
        return nonnull.compareTo(other) <= 0;
    }

    // Precondition: `nonnull' is non-null
    private static <E extends Comparable<? super E>, V> boolean greater(final E nonnull, final E other) {
        if (other == null) return false;
        return nonnull.compareTo(other) > 0;
    }

    // Precondition: `nonnull' is non-null
    private static <E extends Comparable<? super E>, V> boolean greaterEqual(final E nonnull, final E other) {
        if (other == null) return false;
        return nonnull.compareTo(other) >= 0;
    }

    private Node<E,V> child(final E key, final Node<E,V> l) {
        if (l.k[0] == null) return l.c.get(0);

        int left = 0, right = l.kcount-1;
        while (right > left) {
            int mid = (left+right)/2;
            if (key.compareTo((E)l.k[mid]) < 0) {
                right = mid;
            } else {
                left = mid+1;
            }
        }
        if (left == l.kcount-1 && key.compareTo((E)l.k[left]) >= 0) {
            return l.c.get(l.kcount);
        }
        return l.c.get(left);
    }

    private void help(final Info<E,V> info) {
        if (info.getClass() == IInfo.class)      helpInsert((IInfo<E,V>) info);
        else if (info.getClass() == DInfo.class) helpDelete((DInfo<E,V>) info);
        else if (info.getClass() == Mark.class)  helpMarked(((Mark<E,V>) info).dinfo);
    }

    private void helpInsert(final IInfo<E,V> info) {
        info.oldchild.dirty = true;

        // CAS the correct child pointer of p from oldchild to newchild
        info.p.c.compareAndSet(info.pindex, info.oldchild, info.newchild);      // [[ ichild CAS ]]
        infoUpdater.compareAndSet(info.p, info, new Clean<E,V>());              // [[ iunflag CAS ]]
    }

    private boolean helpDelete(final DInfo<E,V> info) {
        final boolean markSuccess = infoUpdater.compareAndSet(
                info.p, info.pinfo, new Mark<E,V>(info));                       // [[ mark CAS ]]
        final Info<E,V> currentPInfo = info.p.info;
        if (markSuccess || (currentPInfo.getClass() == Mark.class
                && ((Mark<E,V>) currentPInfo).dinfo == info)) {
            helpMarked(info);
            return true;
        } else {
            help(currentPInfo);
            infoUpdater.compareAndSet(info.gp, info, new Clean<E,V>());         // [[ backtrack CAS ]]
            return false;
        }
    }

    private void helpMarked(final DInfo<E,V> info) {
        // observe that there are exactly two non-empty children of info.p,
        // so the following test correctly finds the "other" (remaining) node
        Node<E,V> other = info.p.c.get(info.p.kcount);
        for (int i=0;i<info.p.kcount;i++) {
            final Node u = info.p.c.get(i);
            if (u.kcount > 0 && u != info.l) {
                other = u;
                break;
            }
        }

        for (int i=0;i<info.p.kcount;i++) {
            final Node u = info.p.c.get(i);
            if (u != other) u.dirty = true;
        }

        // CAS the correct child pointer of info.gp from info.p to other
        info.gp.c.compareAndSet(info.gpindex, info.p, other);                   // [[ dchild CAS ]]
        infoUpdater.compareAndSet(info.gp, info, new Clean<E,V>());             // [[ dunflag CAS ]]
    }

    public void treeString(StringBuffer sb){
        treeString((Node)root, sb);
    }
    private static void treeString(Node root, StringBuffer sb) {
        if (root == null) {
            sb.append("*");
            return;
        }
        sb.append("(");
        sb.append(root.kcount);
        sb.append(" keys");
        for (int i=0;i<root.kcount;i++) {
            sb.append(",");
            sb.append((root.k[i] == null ? "-" : root.k[i].toString()));
        }
        if (root.c != null) {
            for (int i=0;i<root.c.length();i++) {
                treeString((Node) root.c.get(i), sb);
                sb.append(",");
            }
        }
        sb.append(")");
    }


    /**
     *
     * PRIVATE CLASSES
     *
     */

    public static final class Node<E extends Comparable<? super E>, V> {
        public final int kcount;                          // key count
        public final Object[] k;                          // keys
        public final Object[] v;                          // values
        public final AtomicReferenceArray<Node> c;        // children
        public volatile Info<E,V> info = null;
        public volatile boolean dirty = false;

        /**
         * Constructor for leaf with zero keys.
         */
        Node() {
            this.k = null;
            this.v = null;
            this.c = null;
            kcount = 0;
        }

        /**
         * Constructor for newly created leaves with one key.
         */
        public Node(final Object key, final Object value) {
            this.k = new Object[]{key};
            this.v = new Object[]{value};
            this.c = null;
            this.kcount = 1;
        }

        /**
         * Constructor for the root of the tree.
         *
         * The initial tree consists of 2+4 nodes.
         *   The root node has K children, its K-1 keys are null (infinity),
         *     and its key count is K-1.
         *   The first child of the root, c0, is an internal node with K children.
         *     Its keys are also null, and its key count is K-1.
         *     Its children are all empty leaves (no keys).
         *     c1, c2, ... exist to prevent deletion of this node (root.c0).
         *   The other children of the root are all empty leaves
         *     (to prevent null pointer exceptions).
         *
         * @param root if true, the root is created otherwise, if false,
         *             the root's child root.c0 is created.
         */
        public Node(final int K, final boolean root) {
            this.k = new Object[K-1];
            this.v = null;
            this.kcount = K-1;
            if (root) {
                this.c = new AtomicReferenceArray<Node>(K);
                this.c.set(0, new Node<E,V>(K, false));
                for (int i=1;i<K;i++) {
                    this.c.set(i, new Node<E,V>());
                }
            } else {
                this.c = new AtomicReferenceArray<Node>(K);
                for (int i=0;i<K;i++) { // empty leaves -- prevent deletion of this
                    this.c.set(i, new Node<E,V>());
                }
            }
        }

        /**
         * Constructor for case that <code>(knew,vnew)</code> is being inserted,
         * the leaf's key set is full (<code>l.kcount == K-1</code>), and knew is
         * not in l. This constructor creates a new internal node with K-1 keys and
         * K children sorted by key.
         */
        public Node(final E knew, final Object vnew, final Node<E,V> l) {
            this.kcount = l.kcount;

            // determine which elements of l.k should precede knew (will be 0...i-1)
            int i = 0;
            for (;i<kcount;i++) {
                if (less(knew, (E)l.k[i])) break;
            }
            this.c = new AtomicReferenceArray<Node>(kcount+1);
            // add children with keys preceding knew
            for (int j=0;j<i;j++) {
                this.c.set(j, new Node<E,V>(l.k[j], l.v[j]));
            }
            // add <knew, vnew>
            this.c.set(i, new Node<E,V>(knew, vnew));
            // add children with keys following knew
            for (int j=i;j<kcount;j++) {
                this.c.set(j+1, new Node<E,V>(l.k[j], l.v[j]));
            }

            this.k = new Object[kcount];
            for (int j=0;j<kcount;j++) {
                this.k[j] = this.c.get(j+1).k[0];
            }
            this.v = null; // internal node, so no values are stored here.
        }

        /**
         * Constructor for case that <code>cnew</code> is being inserted, and
         * the leaf's key set is not full.
         * This constructor creates a new leaf with keycount(old leaf)+1 keys.
         *
         * @param knew the key being inserted
         * @param l the leaf into which the key is being inserted
         * @param haskey indicates whether l already has <code>knew</code> as a key
         */
        public Node(final E knew, final V vnew, final Node<E,V> l, final boolean haskey) {
            this.c = null;
            if (haskey) {
                this.kcount = l.kcount;
                this.k = l.k; // all keys are the same (and keys of a node never change)
                this.v = new Object[kcount];
                for (int i=0;i<kcount;i++) {
                    // copy all values from l, writing vnew in the process
                    if (equal(knew, (E)l.k[i])) {
                        for (int j=0;j<kcount;j++) {
                            this.v[j] = l.v[j];
                        }
                        this.v[i] = vnew;
                        break; // assert: this line MUST be executed
                    }
                }
            } else {
                this.kcount = l.kcount+1;
                this.k = new Object[kcount];
                this.v = new Object[kcount];

                // copy all keys and values from l
                // writing <knew,vnew> in the process

                // determine which keys precede knew
                int i = 0;
                for (;i<l.kcount;i++) {
                    if (less(knew, (E)l.k[i])) {
                        break; // l.k[0...i-1] will all precede knew.
                    }
                }

                // write <key,val> pairs preceding <knew,vnew>
                // knew precedes l.k[i], but all of l.k[0...i-1] precede knew.
                for (int j=0;j<i;j++) {
                    this.k[j] = l.k[j];
                    this.v[j] = l.v[j];
                }
                // write <knew,vnew>
                this.k[i] = knew;
                this.v[i] = vnew;
                // write <key,val> pairs following <knew,vnew>
                for (int j=i;j<l.kcount;j++) {
                    this.k[j+1] = l.k[j];
                    this.v[j+1] = l.v[j];
                }
            }
        }

        /**
         * Constructor for the case that a key is being deleted from the
         * key set of a leaf.  This constructor creates a new leaf with
         * keycount(old leaf)-1 sorted keys.
         */
        public Node(final E key, final Node<E,V> l) {
            this.c = null;
            this.kcount = l.kcount - 1;
            this.k = new Object[kcount];
            this.v = new Object[kcount];
            for (int i=0;i<l.kcount;i++) {
                if (equal(key, (E)l.k[i])) {
                    // key i is being removed from l
                    // so everything in l.k[0...i-1] union l.k[i+1...l.kcount-1] remains
                    for (int j=0;j<i;j++) {
                        this.k[j] = l.k[j];
                        this.v[j] = l.v[j];
                    }
                    for (int j=i+1;j<l.kcount;j++) {
                        this.k[j-1] = l.k[j];
                        this.v[j-1] = l.v[j];
                    }
                }
            }
        }

        // Precondition: key is not null
        final boolean hasKey(final E key) {
            for (int i=0;i<kcount;i++) {
                if (equal(key, (E)k[i])) return true;
            }
            return false;
        }

        // Precondition: key is not null
        V getValue(final E key) {
            for (int i=0;i<kcount;i++) {
                if (equal(key, (E)k[i])) return (V)v[i];
            }
            return null;
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            treeString(this, sb);
            return sb.toString();
        }

        @Override
        public final boolean equals(final Object o) {
            if (o == null) return false;
            if (o.getClass() != getClass()) return false;
            Node n = (Node) o;
            if ((n.c == null) != (c == null)) return false;
            if (n.c != null) {
                for (int i=0;i<=kcount;i++) {
                    if (!n.c.get(i).equals(c.get(i))) return false;
                }
            }
            for (int i=0;i<kcount;i++) {
                if (!n.k[i].equals(k[i]) ||
                        !n.v[i].equals(v[i])) return false;
            }
            if ((n.info != null && !n.info.equals(info)) ||
                    n.kcount != kcount) return false;
            return true;
        }

    }

    static interface Info<E extends Comparable<? super E>, V> {}

    static final class IInfo<E extends Comparable<? super E>, V> implements Info<E,V> {
        final Node<E,V> p, oldchild, newchild;
        final int pindex;

        IInfo(final Node<E,V> oldchild, final Node<E,V> p, final Node<E,V> newchild,
              final int pindex) {
            this.p = p;
            this.oldchild = oldchild;
            this.newchild = newchild;
            this.pindex = pindex;
        }

        @Override
        public boolean equals(Object o) {
            IInfo x = (IInfo) o;
            if (x.p != p
                    || x.oldchild != oldchild
                    || x.newchild != newchild
                    || x.pindex != pindex) return false;
            return true;
        }
    }

    static final class DInfo<E extends Comparable<? super E>, V> implements Info<E,V> {
        final Node<E,V> p, l, gp;
        final Info<E,V> pinfo;
        final int gpindex;

        DInfo(final Node<E,V> l, final Node<E,V> p, final Node<E,V> gp,
              final Info<E,V> pinfo, final int gpindex) {
            this.p = p;
            this.l = l;
            this.gp = gp;
            this.pinfo = pinfo;
            this.gpindex = gpindex;
        }

        @Override
        public boolean equals(Object o) {
            DInfo x = (DInfo) o;
            if (x.p != p
                    || x.l != l
                    || x.gp != gp
                    || x.pinfo != pinfo
                    || x.gpindex != gpindex) return false;
            return true;
        }
    }

    static final class Mark<E extends Comparable<? super E>, V> implements Info<E,V> {
        final DInfo<E,V> dinfo;

        Mark(final DInfo<E,V> dinfo) {
            this.dinfo = dinfo;
        }

        @Override
        public boolean equals(Object o) {
            Mark x = (Mark) o;
            if (x.dinfo != dinfo) return false;
            return true;
        }
    }

    static final class Clean<E extends Comparable<? super E>, V> implements Info<E,V> {
        @Override
        public boolean equals(Object o) {
            return (this == o);
        }
    }

}