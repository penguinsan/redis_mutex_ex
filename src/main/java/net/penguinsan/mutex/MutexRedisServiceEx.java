package net.penguinsan.mutex;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;

public class MutexRedisServiceEx extends MutexRedisService {

  private final String sha;

  public MutexRedisServiceEx(
          JedisPool jedisPool,
          long lockTimeSec,
          long timeWaitMillis) {
    super(jedisPool, lockTimeSec, timeWaitMillis);
    try (Jedis r = getJedisPool().getResource()) {
      this.sha = r.scriptLoad(
                "local id = redis.call('GET',KEYS[1])\n"
              + "if id == ARGV[1] then\n"
              +   "redis.call('DEL',KEYS[1])\n"
              +   "return 'delete'\n"
              + "end\n"
              + "return id"
              );
    }
  }

  @Override
  public void releaseMutex(Mutex m) {
    String result;
    try (Jedis r = getJedisPool().getResource()) {
      result = (String) r.evalsha(this.sha, List.of(m.getObjectKey()), List.of(m.getLockId()));
    }
    if (result.equals("delete")) {
      System.out.printf("delete key : lock_id=%s\n", m.getLockId());
    } else {
      System.out.printf("not delete key : lock_id expect=%s actual=%s\n", m.getLockId(), result);
    }
  }
}
