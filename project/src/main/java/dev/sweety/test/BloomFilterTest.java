package dev.sweety.test;

import dev.sweety.core.util.FastScalableCountingBloomFilter;

public class BloomFilterTest {

    public static void main(String[] args) throws Throwable {
        FastScalableCountingBloomFilter scbf = new FastScalableCountingBloomFilter(1024, 4, 2.0);

        String[] data = {"ciao", "hello", "world", "java", "bloom"};
        for (String s : data) scbf.add(s.getBytes());

        System.out.println(scbf);

        for (String s : data) {
            System.out.println("Contains '"+s+"'? " + scbf.contains(s.getBytes()));
        }

        System.out.println("---");

        scbf.remove("ciao".getBytes());

        System.out.println(scbf);

        for (String s : data) {
            System.out.println("Contains '"+s+"'? " + scbf.contains(s.getBytes()));
        }
    }

}
