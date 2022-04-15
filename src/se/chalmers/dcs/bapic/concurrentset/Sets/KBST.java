package se.chalmers.dcs.bapic.concurrentset.Sets;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import se.chalmers.dcs.bapic.concurrentset.utils.*;

public class KBST implements SetADT{

    final Node root;
    final int numChildren;

    public KBST(int numChildren) {
        this.numChildren = numChildren;
        root = new Node(numChildren, true);
    }

    boolean isInternalNode(Node n) {
        return n.children != null;
    }

    Node getChild(final Node n, K key) {
        int i = 0;
        while (i < n.kcount) {
            if (key.compareTo(n.keys[i])) {
                break;
            }
            i++;
        }
        return n.children.get(i);
    }

    int getChildIndex(final Node n, K key) {
        int i = 0;
        while (i < n.kcount) {
            if (key.compareTo(n.keys[i])) {
                break;
            }
            i++;
        }
        return i;
    }

    public final boolean contains(final K key) {
        Node current = root;
        while (isInternalNode(current)) {
            current = getChild(current, key);
        }
        return (current.hasKey(key));
    }

    public final boolean add(K key) {
        // StringBuffer sb = new StringBuffer();
        // treeString(sb);
        // System.out.println(sb);
        // System.out.println("Adding: " + key.getValue());

        Node newNode, parent, ancestor, terminal, successor;

        while (true) {
            ancestor = root;
            successor = parent = root.children.get(0);
            terminal = parent.children.get(0);

            while (isInternalNode(terminal)) {

                /**
                 * The While loop implements the seek() method of the paper
                 */
                if (!terminal.isTagged.get()) {
                    ancestor = parent;
                    successor = terminal;
                }

                parent = terminal;
                terminal = getChild(parent, key);
            }
            if (terminal.hasKey(key)) {
                return false;
            }

            OperationType operationType;
            if (terminal.kcount == numChildren - 1) {
                operationType = OperationType.SPROUTING_INSERT;
            } else {
                operationType = OperationType.SIMPLE_INSERT;
            }
            newNode = new Node(key, terminal, operationType);

            int terminalIndex = getChildIndex(parent, key);

            if (!terminal.isFlagged.get() && !terminal.isTagged.get()) {
                if (parent.children.compareAndSet(terminalIndex, terminal, newNode)) {
                    return true;
                }
            }
            else if (terminal == parent.children.get(terminalIndex) && (terminal.isFlagged.get() || terminal.isTagged.get())) {
                cleanUp(ancestor, successor, parent, key);
            }
        }
    }

    public final boolean remove(K key) {
        // StringBuffer sb = new StringBuffer();
        // treeString(sb);
        // System.out.println(sb);
        // System.out.println("Removing: " + key.getValue());

        int mode = 1;    // 1:- INJECTION, 2:- CLEANUP
        Node parent, terminal, ancestor, successor, newNode = null;
        while (true) {
            ancestor = root;
            successor = parent = root.children.get(0);
            terminal = parent.children.get(0);

            while (isInternalNode(terminal)) {

                /**
                 * The While loop implements the seek() method of the paper
                 */
                if (!terminal.isTagged.get()) {
                    ancestor = parent;
                    successor = terminal;
                }

                parent = terminal;
                terminal = getChild(parent, key);
            }

            if (!terminal.hasKey(key)) {
                return false;
            }

            if (true || terminal.kcount > 1 || parent.getNonEmptyChildCount() != 2) { // SIMPLE_DELETE
                newNode = new Node(key, terminal, OperationType.SIMPLE_DELETE);
                int terminalIndex = getChildIndex(parent, key);

                if (!terminal.isFlagged.get() && !terminal.isTagged.get()) {
                    if (parent.children.compareAndSet(terminalIndex, terminal, newNode)) {
                        return true;
                    }
                }
                else if (terminal == parent.children.get(terminalIndex) 
                        && (terminal.isFlagged.get() || terminal.isTagged.get())) {
                    cleanUp(ancestor, successor, parent, key);
                }
            }
            else if (mode == 1) { // PRUNING DELETE
                // set newNode to flagged terminal node
                newNode = new Node(terminal, true);
                int terminalIndex = getChildIndex(parent, key);

                if (!terminal.isFlagged.get() && !terminal.isTagged.get()) {
                    if (parent.children.compareAndSet(terminalIndex, terminal, newNode)) {
                        if (cleanUp(ancestor, successor, parent, key)) {
                            return true;
                        }
                    }
                }
                else if (terminal == parent.children.get(terminalIndex)
                        && (terminal.isFlagged.get() || terminal.isTagged.get())) {
                    cleanUp(ancestor, successor, parent, key);
                }
            }
            else if (mode == 2) {
                if (terminal == newNode) {
                    if (cleanUp(ancestor, successor, parent, key)) {
                        return true;
                    }
                }
                else {
                    return true;
                }
            }
        }
    }

    /**
     *
     * @param ancestor
     * @param successor
     * @param parent
     * @param key
     * @return
     */
    protected final boolean cleanUp(Node ancestor, Node successor, Node parent, K key) {
        for (int i = 0; i < parent.children.length(); i++) {
            parent.children.get(i).isTagged.set(true);
        }
        Node siblingChild = new Node();
        int count = 0;
        for (int i = 0; i < parent.children.length(); i++) {
            if (!parent.children.get(i).isFlagged.get() && parent.children.get(i).kcount > 0) {
                siblingChild = new Node(parent.children.get(i), false);
                count++;
            }
        }
        if (count <= 1) {
            int successorIndex = getChildIndex(ancestor, key);
            if (ancestor.children.get(successorIndex) == successor && (!successor.isFlagged.get() && !successor.isTagged.get())) {
                return ancestor.children.compareAndSet(successorIndex, successor, siblingChild);
            }
            else {
                return false;
            }
        } 
        else {
            for (int i = 0; i < parent.children.length(); i++) {
                if (parent.children.get(i).isFlagged.get()) {
                    parent.children.set(i, new Node());
                }
                else if (parent.children.get(i).isTagged.get()) {
                    parent.children.get(i).isTagged.set(false);
                }
            }
            return true;
        }
    }

    public boolean traversalTest() {
        return true;
    }

    public void treeString(StringBuffer sb, Node n) {
        if (n == null) {
            sb.append("*");
            return;
        }
        sb.append("(");
        sb.append(n.kcount);
        sb.append(" keys");
        sb.append(" tagged " + n.isTagged.get());
        sb.append(" flagged " + n.isFlagged.get());
        for (int i=0; i < n.kcount; i++) {
            sb.append(",");
            sb.append(n.keys[i].getValue());
        }
        if (n.children != null) {
            for (int i=0; i < n.children.length(); i++) {
                treeString(sb, n.children.get(i));
                sb.append(",");
            }
        }
        sb.append(")");
    }

    public void treeString(StringBuffer sb) {
        treeString(sb, root);
    }

    protected enum OperationType {SIMPLE_INSERT, SPROUTING_INSERT, SIMPLE_DELETE};

    protected static class Node {

        final int kcount;
        final K[] keys;
        volatile AtomicReferenceArray<Node> children;
        volatile AtomicBoolean isFlagged;
        volatile AtomicBoolean isTagged;

        /**
         * Constructor for leaf with zero keys.
         */
        Node () {
            kcount = 0;
            keys = null;
            children = null;
            isFlagged = new AtomicBoolean();
            isTagged = new AtomicBoolean();
        }

        /**
         * Constructor for newly created leaves with one key.
         */
        Node(final K key) {
            kcount = 1;
            keys = new K[]{key};
            children = null;
            isFlagged = new AtomicBoolean();
            isTagged = new AtomicBoolean();
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
        Node(int numChildren, boolean root) {
            keys = new K[numChildren - 1];
            kcount = numChildren - 1;
            for (int i = 0; i < kcount; i++) {
                keys[i] = K.MaxValue0;
            }
            isFlagged = new AtomicBoolean();
            isTagged = new AtomicBoolean();
            if (root) {
                children = new AtomicReferenceArray<Node>(numChildren);
                children.set(0, new Node(numChildren, false));
                for (int i = 1; i < numChildren; i++) {
                    children.set(i, new Node());
                }
            } else {
                children = new AtomicReferenceArray<Node>(numChildren);
                for (int i = 0; i < numChildren; i++) {
                    children.set(i, new Node());
                }
            }
        }

        // Constructor for the operations simple insert, sprouting insert and simple delete
        public Node(final K knew, final Node l, final OperationType operationType) {
            isFlagged = new AtomicBoolean();
            isTagged = new AtomicBoolean();

            if (operationType == OperationType.SPROUTING_INSERT) {
                kcount = l.kcount;                
                // determine which elements of l.k should precede knew (will be 0...i-1)
                int i = 0;
                while (i < kcount) {
                    if (knew.compareTo(l.keys[i])){
                        break;
                    }
                    i++;
                }
                children = new AtomicReferenceArray<Node>(kcount + 1);
                // add children with keys preceding knew
                for (int j = 0; j < i; j++) {
                    children.set(j, new Node(l.keys[j]));
                }
                // add knew
                children.set(i, new Node(knew));
                // add children with keys following knew
                for (int j = i; j < kcount; j++) {
                    children.set(j + 1, new Node(l.keys[j]));
                }
                keys = new K[kcount];
                for (int j=0; j < kcount; j++) {
                    keys[j] = children.get(j+1).keys[0];
                }
            } else if (operationType == OperationType.SIMPLE_INSERT) {
                children = null;
                kcount = l.kcount+1;
                keys = new K[kcount];
                // copy all keys and values from l
                // writing knew in the process
                // determine which keys precede knew
                int i = 0;
                while (i < l.kcount) {
                    if (knew.compareTo(l.keys[i])) {
                        break; // l.k[0...i-1] will all precede knew.
                    }
                    i++;
                }
                // write key preceding knew
                // knew precedes l.k[i], but all of l.k[0...i-1] precede knew.
                for (int j = 0; j < i; j++) {
                    keys[j] = l.keys[j];
                }
                // write knew
                keys[i] = knew;
                // write key following knew
                for (int j = i; j < l.kcount; j++) {
                    keys[j+1] = l.keys[j];
                }
            } else { // SIMPLE_DELETION
                children = null;
                kcount = l.kcount - 1;
                if (kcount == 0) {
                    keys = null;
                } else {
                    keys = new K[kcount];
                    for (int i = 0, j = 0; i < l.kcount; i++) {
                        if (knew.equals(l.keys[i])) continue;
                        keys[j] = l.keys[i];
                        j++;
                    }
                }
            }
        }

        // Constructor to create flagged node
        // @param b we set isFlagged to b
        public Node(Node n, boolean b) {
            this.keys = n.keys;
            this.kcount = n.kcount;
            this.children = n.children;
            this.isFlagged = new AtomicBoolean(b);
            this.isTagged = new AtomicBoolean();
        }

        int getNonEmptyChildCount() {
            int count = 0;
            for (int i = 0; i < children.length(); i++) {
                if (children.get(i).kcount > 0) {
                    count++;
                }
            }
            return count;
        }
        
        boolean hasKey(K key) {
            for (int i = 0; i < kcount; i++) {
                if (key.equals(keys[i])) {
                    return true;
                }
            }
            return false;
        }
    }
}
