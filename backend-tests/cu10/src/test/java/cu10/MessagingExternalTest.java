package cu10;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contratos externos contra sesiones JDBC y HTTP reales; sin clases productivas. */
class MessagingExternalTest {
 static Map<String,Object> fixture;
 static String base=System.getenv().getOrDefault("BACKEND_BASE_URL","http://127.0.0.1:14310");
 static class Client {
   Map<String,String> cookies=new HashMap<>(); String token,header;
   RequestSpecification req(){return RestAssured.given().baseUri(base).cookies(cookies);}
   void csrf(){Response r=req().get("/api/auth/csrf");assertEquals(200,r.statusCode());cookies.putAll(r.cookies());token=r.path("token");header=r.path("headerName");}
   Client(String role){csrf();Response r=req().header(header,token).formParam("email",person(role).get("email")).formParam("password",fixture.get("password")).post("/api/auth/login");assertEquals(204,r.statusCode(),r.asString());cookies.putAll(r.cookies());csrf();}
   Response post(String path,Object body){return req().header(header,token).contentType("application/json").body(body).post(path);}
   Response get(String path){return req().get(path);}
   Response image(long id,byte[] bytes,String mime){return req().header(header,token).multiPart("text","Imagen CU10").multiPart("clientKey",key()).multiPart("image","imagen",bytes,mime).post("/api/conversations/"+id+"/messages");}
 }
 @SuppressWarnings("unchecked") static Map<String,Object> person(String role){return (Map<String,Object>)fixture.get(role);}
 static String key(){return UUID.randomUUID().toString();}
 static long number(Object x){return ((Number)x).longValue();}
 static String path(long id){return "/api/conversations/"+id;}
 static Map<String,Object> send(String text,String key){return Map.of("text",text,"clientKey",key);}
 static long start(Client c,Object product,Object order){Map<String,Object> b=new HashMap<>();b.put("productId",product);if(order!=null)b.put("orderId",order);Response r=c.post("/api/conversations",b);assertEquals(200,r.statusCode(),r.asString());return number(r.path("id"));}
 static String sql(String query)throws Exception {Process p=new ProcessBuilder("python3","scripts/cu10-fixture.py","sql",query).redirectErrorStream(true).start();String out=new String(p.getInputStream().readAllBytes());assertEquals(0,p.waitFor(),out);return out.strip();}
 @BeforeEach void load()throws Exception {Process p=new ProcessBuilder("python3","scripts/cu10-fixture.py").start();String json=new String(p.getInputStream().readAllBytes());assertEquals(0,p.waitFor(),new String(p.getErrorStream().readAllBytes()));fixture=io.restassured.path.json.JsonPath.from(json).getMap("");}
 @Test void contextosYConcurrencia()throws Exception {
   Client b=new Client("buyer");long id=start(b,fixture.get("product"),null);
   assertEquals(id,start(b,fixture.get("product"),null));
   Response t=b.get(path(id));assertEquals(number(person("seller").get("id")),number(t.path("sellerId")));assertEquals(number(fixture.get("store")),number(t.path("storeId")));
   long order=start(b,fixture.get("product"),fixture.get("order"));assertNotEquals(id,order);
   assertNotEquals(order,start(b,fixture.get("otherProduct"),fixture.get("order")));
   assertNotEquals(order,start(b,fixture.get("product"),fixture.get("otherOrder")));
   assertEquals(404,new Client("stranger").post("/api/conversations",Map.of("productId",fixture.get("product"),"orderId",fixture.get("order"))).statusCode());
   assertEquals(404,b.post("/api/conversations",Map.of("productId",Long.MAX_VALUE)).statusCode());
   var pool=Executors.newFixedThreadPool(4);try {var jobs=new ArrayList<Future<Long>>();for(int i=0;i<8;i++)jobs.add(pool.submit(()->start(b,fixture.get("otherProduct"),null)));long created=jobs.getFirst().get();assertNotEquals(id,created);for(var j:jobs)assertEquals(created,j.get());assertEquals(1,b.get("/api/conversations").jsonPath().getList("findAll { it.id == "+created+" }").size());}finally{pool.shutdownNow();}
 }
 @Test void mensajesCursoresIdempotenciaYNotificaciones(){
   Client b=new Client("buyer"),s=new Client("seller");long id=start(b,fixture.get("product"),null);String k=key();
   Response first=b.post(path(id)+"/messages",send("CU10 "+k,k));assertEquals(200,first.statusCode(),first.asString());long m=number(first.path("id"));
   assertEquals(m,number(b.post(path(id)+"/messages",send("CU10 "+k,k)).path("id")));
   assertEquals(409,b.post(path(id)+"/messages",send("Distinto",k)).statusCode());
   Response reply=s.post(path(id)+"/messages",send("Respuesta "+k,key()));assertEquals(200,reply.statusCode(),reply.asString());long r=number(reply.path("id"));
   assertEquals(1,b.get(path(id)+"/messages?after="+m+"&size=1").jsonPath().getList("").size());
   assertEquals(r,number(b.get(path(id)+"/messages?after="+m+"&size=1").path("[0].id")));
   List<Number> ids=b.get(path(id)+"/messages").path("id");for(int i=1;i<ids.size();i++)assertTrue(ids.get(i-1).longValue()<ids.get(i).longValue());
   List<Number> older=b.get(path(id)+"/messages?before="+r).path("id");assertFalse(older.stream().anyMatch(x->x.longValue()>=r));
   assertEquals(404,b.req().header(b.header,b.token).contentType("application/json").body(send("Editar",key())).put(path(id)+"/messages/"+m).statusCode());
   assertEquals(404,b.req().header(b.header,b.token).delete(path(id)+"/messages/"+m).statusCode());
   assertTrue(b.get(path(id)+"/messages").asString().contains("CU10 "+k));
   assertEquals(200,b.get("/api/notifications").statusCode());
   assertTrue(b.get("/api/notifications").jsonPath().getList("items.conversationId",Long.class).contains(id));
 }
 @Test void autorizacionYCsrf(){
   Client b=new Client("buyer"),x=new Client("stranger");long id=start(b,fixture.get("product"),null);
   assertEquals(404,x.get(path(id)).statusCode());assertEquals(404,x.post(path(id)+"/messages",send("Intruso",key())).statusCode());
   assertEquals(200,x.get("/api/conversations").statusCode());assertFalse(x.get("/api/conversations").asString().contains("\"id\":"+id+","));
   assertEquals(401,RestAssured.get(base+path(id)).statusCode());
   Response forged=b.post(path(id)+"/messages",Map.of("text","Identidad auténtica","clientKey",key(),"senderId",person("stranger").get("id")));assertEquals(200,forged.statusCode());assertEquals(number(person("buyer").get("id")),number(forged.path("senderId")));
   assertEquals(403,b.req().contentType("application/json").body(send("Sin CSRF",key())).post(path(id)+"/messages").statusCode());
 }
 @Test void lecturaArchivoYCierre(){
   Client b=new Client("buyer"),s=new Client("seller");long id=start(b,fixture.get("product"),null);
   assertEquals(200,s.post(path(id)+"/archive",Map.of("value",true)).statusCode());
   long m=number(b.post(path(id)+"/messages",send("Lectura",key())).path("id"));assertEquals(false,s.get(path(id)).path("archived"));assertTrue(number(s.get(path(id)).path("unread"))>0);
   assertEquals(200,s.post(path(id)+"/read",Map.of("through",m)).statusCode());assertEquals(0,number(s.get(path(id)).path("unread")));
   assertNotEquals(number(s.get(path(id)).path("readThrough")),number(b.get(path(id)).path("readThrough")));
   assertEquals(200,b.post(path(id)+"/archive",Map.of("value",true)).statusCode());assertEquals(true,b.get(path(id)).path("archived"));assertEquals(false,s.get(path(id)).path("archived"));
   assertEquals(200,b.post(path(id)+"/archive",Map.of("value",false)).statusCode());assertEquals(false,b.get(path(id)).path("archived"));
   assertEquals(200,b.post(path(id)+"/closed",Map.of("value",true)).statusCode());try{assertEquals(409,s.post(path(id)+"/messages",send("Cerrado",key())).statusCode());}finally{assertEquals(200,s.post(path(id)+"/closed",Map.of("value",false)).statusCode());}
   assertEquals(200,s.post(path(id)+"/messages",send("Reabierto",key())).statusCode());
 }
 @Test void validaciones()throws Exception {Client b=new Client("buyer");long id=start(b,fixture.get("product"),null);
   for(var input:List.of(send("",key()),send("texto","corta"),send("x".repeat(4001),key())))assertEquals(400,b.post(path(id)+"/messages",input).statusCode());
   assertEquals(200,b.post(path(id)+"/messages",send("x".repeat(4000),key())).statusCode());
   assertEquals(400,b.image(id,"<script>falso</script>".getBytes(),"image/png").statusCode());
   assertEquals(413,b.image(id,new byte[5242881],"image/png").statusCode());
   for(String name:List.of("side","pixels"))assertEquals(400,b.image(id,Files.readAllBytes(Path.of("tests/cu10-e2e/assets/invalid-"+name+".png")),"image/png").statusCode());
 }
 @Test void restriccionesConLectura()throws Exception {Client b=new Client("buyer"),s=new Client("seller");long id=start(b,fixture.get("product"),null);
   sql("UPDATE user_accounts SET communications_restricted=TRUE WHERE id="+person("buyer").get("id"));
   try{assertEquals(200,b.get(path(id)+"/messages").statusCode());assertEquals(403,b.post(path(id)+"/messages",send("Restringido",key())).statusCode());}finally{sql("UPDATE user_accounts SET communications_restricted=FALSE WHERE id="+person("buyer").get("id"));}
   sql("UPDATE stores SET status='RESTRICTED',status_reason='Fixture CU10' WHERE id="+fixture.get("store"));
   try{assertEquals(200,s.get(path(id)+"/messages").statusCode());assertEquals(403,s.post(path(id)+"/messages",send("Restringida",key())).statusCode());}finally{sql("UPDATE stores SET status='ACTIVE',status_reason=NULL WHERE id="+fixture.get("store"));}
 }
 @Test void imagenesYReportes()throws Exception {Client b=new Client("buyer"),s=new Client("seller"),x=new Client("stranger"),support=new Client("support");long id=start(b,fixture.get("product"),null);
   for(String ext:List.of("png","jpg","webp")){
     byte[] data=Files.readAllBytes(Path.of("tests/cu10-e2e/assets/real."+ext));Response sent=b.image(id,data,"image/"+(ext.equals("jpg")?"jpeg":ext));assertEquals(200,sent.statusCode(),sent.asString());long m=number(sent.path("id"));String image="/api/conversations/images/"+m;
     assertArrayEquals(data,s.get(image).asByteArray());assertEquals(404,x.get(image).statusCode());
     var report=Map.of("contentType","MENSAJE","contentId",Long.toString(m),"reason","CONTENIDO_INAPROPIADO","description","Evidencia externa CU10");
     assertEquals(403,b.post("/api/reports",report).statusCode());assertEquals(404,x.post("/api/reports",report).statusCode());
     Response reported=s.post("/api/reports",report);assertEquals(201,reported.statusCode(),reported.asString());
     assertArrayEquals(data,support.get("/api/support/messaging/images/"+m).asByteArray());
     assertEquals(403,b.get("/api/support/messaging/images/"+m).statusCode());
     sql("INSERT INTO content_moderation_state(content_type,content_id,state,case_id,updated_at) SELECT 'MENSAJE',content_id,'OCULTO_TEMPORAL',case_id,NOW(6) FROM reports WHERE content_type='MENSAJE' AND content_id='"+m+"' LIMIT 1;");
     assertEquals(404,s.get(image).statusCode());assertTrue(s.get(path(id)+"/messages").asString().contains("Mensaje oculto temporalmente por moderación"));
     assertArrayEquals(data,support.get("/api/support/messaging/images/"+m).asByteArray());
   }
 }
 @Test void falloPersistenciaAtomico()throws Exception {Client b=new Client("buyer");long id=start(b,fixture.get("otherProduct"),null);int before=b.get(path(id)+"/messages").jsonPath().getList("").size();String notices=sql("SELECT COUNT(*) FROM notifications WHERE reference_type='CONVERSATION' AND reference_id='"+id+"';");
   sql("CREATE TRIGGER cu10_fail BEFORE INSERT ON notifications FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fallo CU10';");
   try{assertEquals(500,b.post(path(id)+"/messages",send("Fallo",key())).statusCode());assertEquals(before,b.get(path(id)+"/messages").jsonPath().getList("").size());assertEquals(notices,sql("SELECT COUNT(*) FROM notifications WHERE reference_type='CONVERSATION' AND reference_id='"+id+"';"));}finally{sql("DROP TRIGGER cu10_fail;");}
   assertEquals(200,b.post(path(id)+"/messages",send("Recuperado",key())).statusCode());
 }
 @Test void falloExternoNoRevierteNiDuplica()throws Exception {
   Client b=new Client("buyer"),s=new Client("seller");long id=start(b,fixture.get("product"),null);String k=key();
   Response response=b.post(path(id)+"/messages",send("Aviso externo fallido",k));assertEquals(200,response.statusCode(),response.asString());long m=number(response.path("id"));
   String query="SELECT CONCAT(COUNT(*),':',COALESCE(MAX(external_status),''),':',COALESCE(MAX(attempts),0)) FROM notifications WHERE event_key='conversation-message-"+m+"';";
   assertEquals("1:FAILED:1",sql(query));
   assertEquals(m,number(b.post(path(id)+"/messages",send("Aviso externo fallido",k)).path("id")));assertEquals("1:FAILED:1",sql(query));
   assertTrue(s.get(path(id)+"/messages").asString().contains("Aviso externo fallido"));
   assertTrue(s.get("/api/seller/notifications").jsonPath().getList("items.conversationId",Long.class).contains(id));
 }

}
