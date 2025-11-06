package ru.gildina.indexer.monitor;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

public class MemoryMonitor {

    public static void printMemoryStats() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        // Heap memory
        MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
        System.out.println("\n=== Heap Memory Statistics ===");
        System.out.printf("Used: %s / Max: %s%n",
                formatBytes(heapMemory.getUsed()),
                formatBytes(heapMemory.getMax()));
        System.out.printf("Committed: %s / Initial: %s%n",
                formatBytes(heapMemory.getCommitted()),
                formatBytes(heapMemory.getInit()));

        // Non-heap memory
        MemoryUsage nonHeapMemory = memoryBean.getNonHeapMemoryUsage();
        System.out.println("\n=== Non-Heap Memory Statistics ===");
        System.out.printf("Used: %s / Max: %s%n",
                formatBytes(nonHeapMemory.getUsed()),
                formatBytes(nonHeapMemory.getMax()));

        // Memory pools
        System.out.println("\n=== Memory Pools ===");
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : pools) {
            if (pool.getType() == java.lang.management.MemoryType.HEAP) {
                MemoryUsage usage = pool.getUsage();
                System.out.printf("%s: %s / %s (%.1f%%)%n",
                        pool.getName(),
                        formatBytes(usage.getUsed()),
                        formatBytes(usage.getMax()),
                        (usage.getUsed() * 100.0) / usage.getMax());
            }
        }

        // Garbage collectors
        System.out.println("\n=== Garbage Collectors ===");
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gc : gcs) {
            System.out.printf("%s: Collections=%d, Time=%dms%n",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }
    }

    public static void printGCInfo() {
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        System.out.println("\n=== GC Activity ===");
        for (GarbageCollectorMXBean gc : gcs) {
            System.out.printf("%s: %d collections, %d ms total%n",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }
    }

    public static void printHeapInfo() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();

        System.out.printf("Heap: %s used of %s (%.1f%%)%n",
                formatBytes(heap.getUsed()),
                formatBytes(heap.getMax()),
                (heap.getUsed() * 100.0) / heap.getMax());
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static void forceGC() {
        System.out.println("\n=== Forcing GC ===");
        System.gc();
        try {
            Thread.sleep(1000); // Даем время GC завершиться
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        printHeapInfo();
    }
}