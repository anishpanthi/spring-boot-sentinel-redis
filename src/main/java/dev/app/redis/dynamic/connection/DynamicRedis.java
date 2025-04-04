package dev.app.redis.dynamic.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.sentinel.api.StatefulRedisSentinelConnection;
import io.lettuce.core.sentinel.api.sync.RedisSentinelCommands;
import jakarta.annotation.PostConstruct;
import java.net.SocketAddress;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * @author Anish Panthi
 */
@Configuration
@Log4j2
@RequiredArgsConstructor
public class DynamicRedis {

  private final ReentrantLock lock = new ReentrantLock();
  private String currentMasterHost;
  private int redisPort;
  private String redisPassword;
  private String sentinelPassword;

  private LettuceConnectionFactory connectionFactory;

  @PostConstruct
  public void init() {
    log.info("🔄 Initializing Redis Configuration...");
    updateRedisMaster();
  }

  @Scheduled(fixedRate = 30000) // Check every 30 seconds
  public void checkForMasterChange() {
    log.info("🔄 Checking for Redis Master Node Change...");
    updateRedisMaster();
  }

  private void updateRedisMaster() {
    lock.lock();
    try {
      JsonNode credentials = getRedisCredentials();
      if (credentials == null) {
        log.error("❌ Redis credentials not found in VCAP_SERVICES!");
        return;
      }

      JsonNode sentinels = credentials.get("sentinels");
      if (sentinels == null) {
        log.error("❌ No sentinel nodes found!");
        return;
      }

      String masterName = credentials.get("master_name").asText();
      String sentinelPassword = credentials.get("sentinel_password").asText();
      String password = credentials.get("password").asText();

      log.info("🔍 Starting sentinels scan...");
      for (JsonNode sentinel : sentinels) {
        String host = sentinel.get("host").asText();
        int port = sentinel.get("port").asInt();

        log.info("🔍 Checking Sentinel at {}:{}", host, port);

        String newMasterHost = getMasterNode(host, port, masterName, sentinelPassword);
        log.info("🟢 Found Redis Master Node: {}", newMasterHost);
        if (newMasterHost != null && !newMasterHost.equals(currentMasterHost)) {
          log.info("🟢 Redis Master changed to: {}", newMasterHost);
          currentMasterHost = newMasterHost;
          redisPort = port;
          redisPassword = password;
          this.sentinelPassword = sentinelPassword;
          refreshRedisConnection();
          return;
        }
      }
    } catch (Exception e) {
      log.error("❌ Error detecting Redis master node", e);
    } finally {
      lock.unlock();
    }
  }

  private JsonNode getRedisCredentials() {
    try {
      String vcap = System.getenv("VCAP_SERVICES");
      log.info("VCAP_SERVICES: {}", vcap);
      if (vcap == null || vcap.isEmpty()) {
        return null;
      }

      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode rootNode = objectMapper.readTree(vcap);

      return rootNode.has("p.redis")
          ? rootNode.get("p.redis").get(0).get("credentials")
          : rootNode.get("p-redis").get(0).get("credentials");
    } catch (Exception e) {
      log.error("❌ Error parsing VCAP_SERVICES JSON", e);
      return null;
    }
  }

  private String getMasterNode(String host, int port, String masterName, String sentinelPassword) {
    try {
      RedisURI redisUri =
          RedisURI.builder()
              .withHost(host)
              .withPort(port)
              .withPassword(sentinelPassword.toCharArray())
              .build();

      RedisClient redisClient = RedisClient.create(redisUri);
      StatefulRedisSentinelConnection<String, String> sentinelConnection =
          redisClient.connectSentinel();
      log.info("😊 Is connection open: {}", sentinelConnection.isOpen());
      RedisSentinelCommands<String, String> sentinelCommands = sentinelConnection.sync();

      log.info("🔍 Extracting master node host...");
      SocketAddress address = sentinelCommands.getMasterAddrByName(masterName);
      sentinelConnection.close();
      redisClient.shutdown();

      if (address != null) {
        return address.toString().replace("/<unresolved>", "").split(":")[0];
      }
    } catch (Exception e) {
      log.error("❌ Error connecting to Redis sentinel at {}:{}", host, port, e);
    }
    return null;
  }

  private void refreshRedisConnection() {
    log.info("🔄 Refreshing Redis Connection...");
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }

    log.info(
        "🟢 Creating new Redis Connection using host: {}, at port: {}, with password: {}",
        currentMasterHost,
        redisPort,
        redisPassword);
    RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
    redisConfig.setHostName(currentMasterHost);
    redisConfig.setPort(6379);
    redisConfig.setPassword(redisPassword);


    connectionFactory = new LettuceConnectionFactory(redisConfig);
    connectionFactory.afterPropertiesSet();
  }

  @Bean
  public RedisConnectionFactory redisConnectionFactory() {
    return connectionFactory;
  }

  @Bean
  public RedisTemplate<String, String> redisTemplate(
      RedisConnectionFactory redisConnectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new StringRedisSerializer());
    return template;
  }
}
