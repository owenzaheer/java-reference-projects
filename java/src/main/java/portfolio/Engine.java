package portfolio;
import java.time.Instant;
import java.util.*;
public final class Engine {
 public static final class Problem extends RuntimeException {public final int status;public Problem(int status,String message){super(message);this.status=status;}}
 private int stock=10,stockVersion=0;
 private final Map<String,Map<String,Object>> orders=new LinkedHashMap<>(),grades=new LinkedHashMap<>();
 private final List<Map<String,Object>> outbox=new ArrayList<>(),audit=new ArrayList<>();
 private final Map<String,Deque<Long>> limits=new HashMap<>();
 private static Map<String,Object> row(Object... values){var r=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)r.put((String)values[i],values[i+1]);return r;}
 private static String text(Map<String,Object> p,String key){if(p.get(key) instanceof String s&&!s.isBlank()&&s.length()<=100)return s;throw new Problem(422,key+" is required");}
 private static long integer(Map<String,Object> p,String key,long min){if(p.get(key) instanceof Number n&&(n instanceof Integer||n instanceof Long)&&n.longValue()>=min)return n.longValue();throw new Problem(422,key+" must be an integer >= "+min);}
 private static Map<String,Object> find(Map<String,Map<String,Object>> table,String id){var r=table.get(id);if(r==null)throw new Problem(404,"Record not found");return r;}
 private static List<Map<String,Object>> copies(Collection<Map<String,Object>> rows){return rows.stream().map(HashMap::new).map(x->(Map<String,Object>)x).toList();}
 public synchronized Map<String,Object> state(){return row("stock",stock,"stockVersion",stockVersion,"orders",copies(orders.values()),"grades",copies(grades.values()),"outbox",copies(outbox),"audit",copies(audit));}
 public synchronized Map<String,Object> command(String action,Map<String,Object> p,String role){var result=execute(action,p,role);audit.add(row("role",role,"action",action,"detail",new HashMap<>(p)));return new HashMap<>(result);}
 private Map<String,Object> execute(String action,Map<String,Object> p,String role){
  if(action.equals("reserve")){
   var id=text(p,"id");var sku=text(p,"sku");long qty=integer(p,"quantity",1);var old=orders.get(id);
   if(old!=null){if(!old.get("sku").equals(sku)||((Number)old.get("quantity")).longValue()!=qty)throw new Problem(409,"Conflicting idempotency key");return old;}
   if(!sku.equals("BOOK")||integer(p,"version",0)!=stockVersion||qty>stock)throw new Problem(409,"Insufficient stock or stale version");stock-=qty;stockVersion++;
   var r=row("id",id,"sku",sku,"quantity",qty,"status","reserved","version",0,"expires",Instant.now().getEpochSecond()+300);orders.put(id,r);outbox.add(row("id",id,"kind","order.reserved","delivered",false));return r;
  }
  if(Set.of("ship","cancel","refund").contains(action)){
   if(!role.equals("operator"))throw new Problem(403,"Operator required");var r=find(orders,text(p,"id"));var target=action.equals("ship")?"shipped":action.equals("cancel")?"cancelled":"refunded";
   if(r.get("status").equals(target))return r;
   if(!r.get("status").equals(action.equals("refund")?"shipped":"reserved")||((Number)r.get("version")).longValue()!=integer(p,"version",0))throw new Problem(409,"Invalid transition or stale version");
   if(action.equals("ship")&&((Number)r.get("expires")).longValue()<Instant.now().getEpochSecond())throw new Problem(409,"Reservation expired");
   if(action.equals("cancel")){stock+=((Number)r.get("quantity")).intValue();stockVersion++;}r.put("status",target);r.put("version",((Number)r.get("version")).intValue()+1);outbox.add(row("id",r.get("id"),"kind","order."+target,"delivered",false));return r;
  }
  if(action.equals("relay")){
   if(!role.equals("operator"))throw new Problem(403,"Operator required");if(Boolean.TRUE.equals(p.get("simulateFailure")))throw new Problem(503,"Fixture broker outage; messages retained");var pending=outbox.stream().filter(r->!Boolean.TRUE.equals(r.get("delivered"))).toList();var result=row("delivered",copies(pending));pending.forEach(r->r.put("delivered",true));return result;
  }
  if(action.equals("submit")){
   if(!Set.of("learner","instructor").contains(role))throw new Problem(403,"Learner required");var id=text(p,"id");var student=text(p,"student");if(integer(p,"deadline",0)<Instant.now().getEpochSecond())throw new Problem(422,"Submission deadline passed");
   if(!(p.get("answers") instanceof List<?> answers)||answers.size()!=3||answers.stream().anyMatch(a->!(a instanceof String)))throw new Problem(422,"Three answer strings required");if(grades.containsKey(id))throw new Problem(409,"Repeated submission");var expected=List.of("transaction","idempotency","audit");int score=0;for(int i=0;i<3;i++)if(((String)answers.get(i)).trim().toLowerCase(Locale.ROOT).equals(expected.get(i)))score++;
   var r=row("id",id,"student",student,"score",score,"status","pending","feedback","","version",0);grades.put(id,r);return r;
  }
  if(Set.of("feedback","publish").contains(action)){
   if(!role.equals("instructor"))throw new Problem(403,"Instructor required");var r=find(grades,text(p,"id"));if(((Number)r.get("version")).longValue()!=integer(p,"version",0))throw new Problem(409,"Stale assessment version");
   if(action.equals("feedback")){r.put("feedback",text(p,"feedback"));r.put("status","review");}else{if(!r.get("status").equals("review"))throw new Problem(409,"Feedback review required");r.put("status","published");}r.put("version",((Number)r.get("version")).intValue()+1);return r;
  }
  if(action.equals("request")){
   if(!role.equals("partner"))throw new Problem(403,"Partner role required");if(!text(p,"version").equals("v1"))throw new Problem(422,"Supported contract version: v1");var partner=text(p,"partner");long delay=integer(p,"simulateDelayMs",0);var now=System.currentTimeMillis();var history=limits.computeIfAbsent(partner,k->new ArrayDeque<>());while(!history.isEmpty()&&history.peekFirst()<now-60000)history.removeFirst();if(history.size()>=3)throw new Problem(429,"Three requests per minute in the local fixture");history.addLast(now);
   if(delay>100)throw new Problem(504,"Fixture downstream exceeds 100ms deadline");if(Boolean.TRUE.equals(p.get("simulateOutage")))throw new Problem(502,"Fixture downstream unavailable");return row("version","v1","partner",partner,"status","accepted","traceId",UUID.randomUUID().toString());
  }
  throw new Problem(404,"Unknown action");
 }
}
