package se.chalmers.dcs.bapic.concurrentset.test;

import se.chalmers.dcs.bapic.concurrentset.Sets.*;

public class TrevorBrownTest {
    public static void main(String args[]){
        LockFreeKSTRQ<Double, Double> set = new LockFreeKSTRQ<Double, Double>(4);
////        System.out.println("set created");
////        System.out.println(set.add(new K(1)));
////        System.out.println(set.add(new K(1)) + " == false");
////        System.out.println(set.add(new K(2)));
////        System.out.println(set.contains(new K(1)));
////        System.out.println(set.contains(new K(3)) + " == false");
////        System.out.println(set.remove(new K(1)));
////        System.out.println(set.remove(new K(3)) + " == false");
        for (int i = 0; i < 100; i++) {
            set.add((double) i);
            System.out.println("Added " + i);
            StringBuffer sb = new StringBuffer();
            set.treeString(sb);
            System.out.println(sb);
            System.out.println(set.remove((double) (i - 5)));
            System.out.println("Removed " + (i-5));
            StringBuffer sb1 = new StringBuffer();
            set.treeString(sb1);
            System.out.println(sb1);
        }
        StringBuffer sb = new StringBuffer();
        set.treeString(sb);
        System.out.println(sb);
    }
}
