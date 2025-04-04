//package dev.app.redis.dynamic.connection;
//
///**
// * @author Anish Panthi
// */
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.lettuce.core.RedisClient;
//import io.lettuce.core.RedisURI;
//import io.lettuce.core.sentinel.api.StatefulRedisSentinelConnection;
//import io.lettuce.core.sentinel.api.sync.RedisSentinelCommands;
//import jakarta.annotation.PostConstruct;
//import java.net.SocketAddress;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.log4j.Log4j2;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.serializer.StringRedisSerializer;
//
///**
// * @author Anish Panthi
// */
//@Configuration
//@Log4j2
//@RequiredArgsConstructor
//public class RedisConfig {
//
//  @PostConstruct
//  public void configureRedis() {
//    try {
//      String vcap = System.getenv("VCAP_SERVICES");
//      log.info("VCAP_SERVICES received: {}", vcap);
//
//      if (vcap == null || vcap.isEmpty()) {
//        log.error("❌ VCAP_SERVICES environment variable is missing or empty.");
//        throw new RuntimeException("Missing VCAP_SERVICES");
//      }
//
//      ObjectMapper objectMapper = new ObjectMapper();
//      JsonNode rootNode = objectMapper.readTree(vcap);
//
//      JsonNode redisNode =
//          rootNode.has("p.redis") ? rootNode.get("p.redis").get(0) : rootNode.get("p-redis").get(0);
//      JsonNode credentialsNode = redisNode.get("credentials");
//      if (credentialsNode == null) {
//        log.error("❌ No credentials found for Redis in VCAP_SERVICES");
//        throw new RuntimeException("Redis credentials missing in VCAP_SERVICES");
//      }
//      log.info("Redis credentials: {}", credentialsNode);
//      JsonNode sentinelsNode = credentialsNode.get("sentinels");
//      if (sentinelsNode == null) {
//        log.error("❌ No sentinel nodes found for Redis in VCAP_SERVICES");
//        throw new RuntimeException("❌ Redis credentials missing in VCAP_SERVICES");
//      }
//      for (JsonNode sentinel : sentinelsNode) {
//        String host = sentinel.get("host").asText();
//        int port = sentinel.get("port").asInt();
//        String sentinelPassword = credentialsNode.get("sentinel_password").asText();
//        String masterName = credentialsNode.get("master_name").asText();
//        String password = credentialsNode.get("password").asText();
//
//        log.info("😊 Trying Sentinel Host: {} Port: {}", host, port);
//
//        if (checkMasterConnection(host, port, masterName, password, sentinelPassword)) {
//          log.info("😊 Redis Master identified at {}:{}", host, port);
//          return;
//        } else {
//          log.warn("⚠️ Redis Sentinel at {}:{} is not the Master. Trying next...", host, port);
//        }
//      }
//      log.error("❌ No valid Redis master found. Configuration failed.");
//    } catch (Exception e) {
//      log.error("❌ Error parsing VCAP_SERVICES JSON or connecting to Redis", e);
//      throw new RuntimeException("Failed to configure Redis connection", e);
//    }
//  }
//
//  private boolean checkMasterConnection(
//      String host, int port, String masterName, String password, String sentinelPassword) {
//    try {
//      RedisURI redisUri =
//          RedisURI.builder()
//              .withHost(host)
//              .withPort(port)
//              .withPassword(sentinelPassword.toCharArray())
//              .build();
//      RedisClient redisClient = RedisClient.create(redisUri);
//
//      StatefulRedisSentinelConnection<String, String> sentinelConnection =
//          redisClient.connectSentinel();
//      log.info("😊 Is connection open: {}", sentinelConnection.isOpen());
//      RedisSentinelCommands<String, String> sentinelCommands = sentinelConnection.sync();
//
//      SocketAddress address = sentinelCommands.getMasterAddrByName(masterName);
//      String[] redisMasterHost;
//      if (address != null && !address.toString().isBlank()) {
//        log.info("😊 Found Master Node: {}", address.toString());
//        redisMasterHost = address.toString().replace("/<unresolved>", "").split(":");
//        log.info("😊 Redis master host is: {}", redisMasterHost[0]);
//        log.info("😊 Set system properties for Redis connection...");
//
//        System.setProperty("redis.host", redisMasterHost[0]);
//        System.setProperty("redis.port", String.valueOf(port));
//        System.setProperty("redis.password", password);
//        sentinelConnection.close();
//        redisClient.shutdown();
//        return true;
//      }
//      sentinelConnection.close();
//      redisClient.shutdown();
//      return false;
//    } catch (Exception e) {
//      log.error("❌ Error connecting to Redis node at {}:{}", host, port, e);
//      return false;
//    }
//  }
//
//  @Bean
//  public RedisTemplate<String, String> redisTemplate(
//      RedisConnectionFactory redisConnectionFactory) {
//    RedisTemplate<String, String> template = new RedisTemplate<>();
//    template.setConnectionFactory(redisConnectionFactory);
//    template.setKeySerializer(new StringRedisSerializer());
//    template.setValueSerializer(new StringRedisSerializer());
//    return template;
//  }
//}
