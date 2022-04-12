package se.chalmers.dcs.bapic.concurrentset.test;

import se.chalmers.dcs.bapic.concurrentset.utils.*;
import se.chalmers.dcs.bapic.concurrentset.Sets.*;

public class KBSTTest {
    public static void main(String[] args) {
        SetADT set = new KBST(4);
        System.out.println("set created");
        System.out.println(set.add(new K(1)));
        System.out.println(set.add(new K(1)) + " == false");
        System.out.println(set.add(new K(2)));
        System.out.println(set.contains(new K(1)));
        System.out.println(set.contains(new K(3)) + " == false");
        System.out.println(set.remove(new K(1)));
        System.out.println(set.remove(new K(3)) + " == false");
    }
}
