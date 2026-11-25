package security;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import io.javalin.http.ServiceUnavailableResponse;
import java.net.URI;
import java.time.Instant;

// Gemmer tilbagekaldte token-ID'er i Redis.
public class TokenRevocationStore {
    private final URI redisAddress = URI.create(AuthSettings.REDIS_URL);

    // Sætter tokenets ID på blacklisten, indtil tokenet udløber.
    public void revoke(String tokenId, Instant expiresAt) {
        long secondsLeft = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        // Et allerede udløbet token behøver ikke at blive gemt.
        if (secondsLeft <= 0) {
            return;
        }
        try (Jedis redis = new Jedis(redisAddress)) {
            // Redis fjerner automatisk nøglen, når tiden er gået.
            redis.setex("revoked:" + tokenId, secondsLeft, "true");
        } catch (JedisException exception) {
            throw new ServiceUnavailableResponse("Logout kunne ikke gennemføres");
        }
    }

    // Returnerer true, hvis tokenets ID findes på blacklisten.
    public boolean isRevoked(String tokenId) {
        try (Jedis redis = new Jedis(redisAddress)) {
            return redis.exists("revoked:" + tokenId);
        } catch (JedisException exception) {
            // Hvis Redis er nede, gives der ikke adgang med et muligvis tilbagekaldt token.
            throw new ServiceUnavailableResponse("Token-kontrollen er utilgængelig");
        }
    }
}
