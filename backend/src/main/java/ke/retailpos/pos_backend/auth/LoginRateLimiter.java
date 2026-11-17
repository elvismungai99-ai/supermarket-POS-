package ke.retailpos.pos_backend.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

  public static final int MAX_ATTEMPTS = 5;
  public static final int MAX_IP_ATTEMPTS = 20;
  public static final long LOCKOUT_DURATION_MS = 60_000L; // 60 seconds lockout

  private static class Attempt {
    int count = 0;
    long lockUntil = 0L;
  }

  private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
  private final Map<String, Attempt> ipAttempts = new ConcurrentHashMap<>();

  public boolean isBlocked(String key) {
    return isKeyBlocked(attempts, key);
  }

  public boolean isBlocked(String username, String clientIp) {
    return isKeyBlocked(attempts, username)
        || (clientIp != null && !clientIp.isBlank() && isKeyBlocked(ipAttempts, "ip:" + clientIp));
  }

  public long getRemainingLockSeconds(String key) {
    return getKeyRemainingLockSeconds(attempts, key);
  }

  public long getRemainingLockSeconds(String username, String clientIp) {
    long u = getKeyRemainingLockSeconds(attempts, username);
    long ip = (clientIp != null && !clientIp.isBlank())
        ? getKeyRemainingLockSeconds(ipAttempts, "ip:" + clientIp)
        : 0L;
    return Math.max(u, ip);
  }

  public int getFailedAttempts(String key) {
    if (key == null) return 0;
    Attempt att = attempts.get(key.toLowerCase());
    return att != null ? att.count : 0;
  }

  public void recordFailedAttempt(String key) {
    recordKeyAttempt(attempts, key, MAX_ATTEMPTS);
  }

  public void recordFailedAttempt(String username, String clientIp) {
    recordKeyAttempt(attempts, username, MAX_ATTEMPTS);
    if (clientIp != null && !clientIp.isBlank()) {
      recordKeyAttempt(ipAttempts, "ip:" + clientIp, MAX_IP_ATTEMPTS);
    }
  }

  public void reset(String key) {
    if (key == null) return;
    attempts.remove(key.toLowerCase());
  }

  public void reset(String username, String clientIp) {
    reset(username);
    if (clientIp != null && !clientIp.isBlank()) {
      ipAttempts.remove(("ip:" + clientIp).toLowerCase());
    }
  }

  private boolean isKeyBlocked(Map<String, Attempt> map, String key) {
    if (key == null) return false;
    String clean = key.toLowerCase();
    Attempt att = map.get(clean);
    if (att == null) return false;

    long now = System.currentTimeMillis();
    if (att.lockUntil > now) {
      return true;
    }
    if (att.lockUntil > 0 && att.lockUntil <= now) {
      map.remove(clean);
      return false;
    }
    return false;
  }

  private long getKeyRemainingLockSeconds(Map<String, Attempt> map, String key) {
    if (key == null) return 0;
    Attempt att = map.get(key.toLowerCase());
    if (att == null) return 0;
    long diff = att.lockUntil - System.currentTimeMillis();
    return diff > 0 ? (diff + 999) / 1000 : 0;
  }

  private void recordKeyAttempt(Map<String, Attempt> map, String key, int maxAllowed) {
    if (key == null) return;
    String cleanKey = key.toLowerCase();
    map.compute(cleanKey, (k, att) -> {
      if (att == null) att = new Attempt();
      att.count++;
      if (att.count >= maxAllowed) {
        att.lockUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
      }
      return att;
    });
  }
}
