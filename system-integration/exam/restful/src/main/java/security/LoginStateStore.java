package security;

import io.javalin.http.ServiceUnavailableResponse;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import java.net.URI;

// Gemmer midlertidig login-state og PKCE-verifier i Redis.
public class LoginStateStore {
    private final URI redisAddress = URI.create(AuthSettings.REDIS_URL);

    // Gemmer loginets state og PKCE-verifier i 5 minutter (300 sekunder).
    public void saveLogin(String state, String verifier) {
        try (Jedis redis = new Jedis(redisAddress)) {
            redis.setex("oauth:state:" + state, 300, verifier);
        } catch (JedisException exception) {
            throw new ServiceUnavailableResponse("Login-lageret er utilgængeligt");
        }
    }

    // Henter og sletter loginoplysningerne i én operation, så de kun kan bruges én gang.
    public String consumeLogin(String state) {
        try (Jedis redis = new Jedis(redisAddress)) {
            return redis.getDel("oauth:state:" + state);
        } catch (JedisException exception) {
            throw new ServiceUnavailableResponse("Login-lageret er utilgængeligt");
        }
    }
}
