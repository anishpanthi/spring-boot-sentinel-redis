package dev.app.redis.dynamic.connection;

/**
 * @author Anish Panthi
 */
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class RedisController {

  private final RedisService redisService;

  @PostMapping("/set")
  public String setKey(@RequestParam String key, @RequestParam String value) {
    redisService.saveValue(key, value);
    return "Key: " + key + " stored successfully!";
  }

  @GetMapping("/get")
  public String getKey(@RequestParam String key) {
    String value = redisService.getValue(key);
    return value != null ? "Value: " + value : "Key not found!";
  }

  @DeleteMapping("/delete")
  public String deleteKey(@RequestParam String key) {
    redisService.deleteValue(key);
    return "Key: " + key + " deleted!";
  }
}
