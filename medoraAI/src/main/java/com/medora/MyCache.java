package com.medora;

// Online Java Compiler (Editor)
// Write and run Java online using this editor.
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class MyCache{
    static Map<String, String> cache = new ConcurrentHashMap<>();
    static AtomicInteger dbCalls = new AtomicInteger();

  synchronized   static String getDevice(String id) {


        String v = cache.get(id);

       if(v==null)
       {
                v = loadFromDb(id);
                cache.put(id, v);

        }

        return v;
    }

    synchronized static String loadFromDb(String id) {


        dbCalls.incrementAndGet();
        try { Thread.sleep(50); } catch (InterruptedException e) { }
        return "device-" + id;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 1000; i++) pool.submit(() -> getDevice("42"));

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("DB calls: " + dbCalls.get());
    }
}
