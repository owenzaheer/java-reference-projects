package portfolio;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.util.*;
@SpringBootApplication
@RestController
public class Application {
 private final Engine engine=new Engine();private final Map<String,Object> config;
 public Application(@Value("${demo.config:ecommerce-order-and-fulfillment-service-java/project.json}")String path)throws Exception{config=new ObjectMapper().readValue(Files.readString(Path.of(path)),Map.class);}
 public static void main(String[] args){SpringApplication.run(Application.class,args);}
 @GetMapping(value="/",produces="text/html")String page()throws Exception{return Files.readString(Path.of("../ui/console.html")).replace("__CONFIG__",new ObjectMapper().writeValueAsString(config));}
 @GetMapping("/api/config")Map<String,Object> config(){return config;}
 @GetMapping("/api/health")Map<String,Object> health(){return Map.of("status","ok","project",config.get("id"),"storage","in-memory synthetic fixture");}
 @GetMapping("/api/state")ResponseEntity<?> state(@RequestHeader(value="Authorization",defaultValue="")String auth){return auth.equals("Bearer local-operator")?ResponseEntity.ok(engine.state()):ResponseEntity.status(403).body(Map.of("detail","Operator required"));}
 @PostMapping("/api/action")ResponseEntity<?> command(@RequestBody Map<String,Object> body,@RequestHeader(value="Authorization",defaultValue="")String auth){
  var role=Map.of("Bearer local-learner","learner","Bearer local-operator","operator","Bearer local-instructor","instructor","Bearer local-partner","partner").get(auth);if(role==null)return ResponseEntity.status(401).body(Map.of("detail","Explicit local fixture token required"));
  try{if(!(body.get("action") instanceof String action)||!((List<?>)config.get("actions")).contains(action))throw new Engine.Problem(404,"Action unavailable in this project");if(!(body.get("payload") instanceof Map<?,?> payload))throw new Engine.Problem(422,"Object payload required");return ResponseEntity.ok(engine.command(action,(Map<String,Object>)payload,role));}
  catch(Engine.Problem e){return ResponseEntity.status(e.status).body(Map.of("detail",e.getMessage()));}
 }
}
