package net.penguinsan.mutex;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Main {

  public static void main(String args[]) {
    final JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(16);
    final JedisPool pool = new JedisPool(config, "localhost", 6379);
    final long lockTimeSec = 5L; // ロックの最長時間は5秒（5秒経っても能動的にUnlockされない場合は強制的にUnlockされる）
    final long timeWaitMillis = 15000L; // ロック獲得のために待つのは15秒まで。15秒待っても獲得できない場合はExceptionがスローされる
    // ロック最長時間を超える処理をした場合（簡易Unlock処理）
    final MutexService mutexService = new MutexRedisService(pool, lockTimeSec, timeWaitMillis);
    Main main = new Main(mutexService);
    List<CompletableFuture<Void>> cfs = new ArrayList<>();
    System.out.println("start(long lock)");
    cfs.add(CompletableFuture.runAsync(() -> main.longLock(1, 7000L)));
    sleep(10);
    cfs.add(CompletableFuture.runAsync(() -> main.longLock(2, 3500L)));
    sleep(10);
    cfs.add(CompletableFuture.runAsync(() -> main.longLock(3, 3500L)));
    CompletableFuture.allOf(cfs.toArray(new CompletableFuture[cfs.size()])).join();
    System.out.println("finish(long lock)");
    // ロック最長時間を超える処理をした場合（自分のlockの場合のみUnlock処理）
    final MutexService mutexServiceEx = new MutexRedisServiceEx(pool, lockTimeSec, timeWaitMillis);
    Main mainEx = new Main(mutexServiceEx);
    cfs.clear();
    System.out.println("start(long lock ex)");
    cfs.add(CompletableFuture.runAsync(() -> mainEx.longLock(1, 7000L)));
    sleep(10);
    cfs.add(CompletableFuture.runAsync(() -> mainEx.longLock(2, 3500L)));
    sleep(10);
    cfs.add(CompletableFuture.runAsync(() -> mainEx.longLock(3, 3500L)));
    CompletableFuture.allOf(cfs.toArray(new CompletableFuture[cfs.size()])).join();
    System.out.println("finish(long lock ex)");
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
    }
  }

  private static final String MUTEX_OBJECT = "key";

  private final MutexService mutexService;

  public Main(MutexService mutexService) {
    this.mutexService = mutexService;
  }

  private void longLock(int n, long timeMillis) {
    System.out.printf("wait %d\n", n);
    try (Mutex m = this.mutexService.waitForSingleObject(MUTEX_OBJECT)) {
      System.out.printf("Lock by %d\n", n);
      processImpl(n, timeMillis);
      System.out.printf("Unlock by %d\n", n);
    }
  }

  private void processImpl(int n, long timeMillis) {
    System.out.printf("process start %d\n", n);
    sleep(timeMillis);
    System.out.printf("process end %d\n", n);
  }
}
