package com.transformersas.integration.profile;

import com.transformersas.integration.support.MarketplaceClient;
import groovy.json.JsonOutput;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Preparación y observación restringidas al entorno desechable CU-09. */
final class ProfileFixture {
    static final String PASSWORD="Cu08RealTestPassword!";
    static final String PATH="/api/account/profile";
    static String json(Map<String,?> value) { return JsonOutput.toJson(value); }
    static String fixture(String... args) throws Exception {
        var command=new ArrayList<>(List.of("python3","scripts/cu09-fixture.py"));
        command.addAll(List.of(args));
        var p=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String result=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
        assertEquals(0,p.waitFor(),"Falló la fixture aislada CU09"); return result;
    }
    static String sql(String query) throws Exception { return fixture("sql",query); }
    static String account() throws Exception { return fixture(); }
    static long id(String email) throws Exception {
        assertTrue(email.matches("cu09-[a-z0-9-]+@example\\.test"));
        return Long.parseLong(sql("SELECT id FROM user_accounts WHERE email='"+email+"';"));
    }
    static MarketplaceClient login(String email) {
        var c=MarketplaceClient.visitor();c.authenticate(email,PASSWORD).then().statusCode(204);return c;
    }
    static Map<String,Object> body(MarketplaceClient c) {
        var response=c.get(PATH);response.then().statusCode(200);
        Map<String,Object> p=response.jsonPath().getMap("");
        var b=new LinkedHashMap<String,Object>();
        for(String key:List.of("fullName","birthDate","email","phone","promotions","marketplaceNews","version")) b.put(key,p.get(key));
        b.put("currentPassword",PASSWORD);b.put("confirmChanges",true);return b;
    }
    static String close(MarketplaceClient c) {
        return json(Map.of("currentPassword",PASSWORD,"version",body(c).get("version"),"confirmClosure",true));
    }
    static String mail(String email,String... except) throws Exception {
        String url=System.getenv("CU09_MAIL_URL");assertNotNull(url,"Falta Mailpit aislado");
        for(int i=0;i<60;i++) {
            var list=given().get(url+"/api/v1/messages");list.then().statusCode(200);
            for(Map<String,Object> m:list.jsonPath().<Map<String,Object>>getList("messages")) {
                if(!m.get("To").toString().contains(email)) continue;
                var detail=given().get(url+"/api/v1/message/"+m.get("ID"));detail.then().statusCode(200);
                var match=Pattern.compile("(?:^|\\s)([A-Za-z0-9_-]{43})(?=\\s|$)",Pattern.MULTILINE).matcher(detail.jsonPath().getString("Text"));
                if(match.find()&&!List.of(except).contains(match.group(1)))return match.group(1);
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No llegó el correo CU09; contenido reservado");
    }
    static String requestEmail(MarketplaceClient c,String next) throws Exception {
        var b=body(c);b.put("email",next);
        c.put(PATH,json(b)).then().statusCode(200).body("verificationDeliveryFailed",org.hamcrest.Matchers.is(false));
        return mail(next,"");
    }
    static long order(String email,String state) throws Exception {
        long a=id(email);
        return Long.parseLong(sql("""
            START TRANSACTION;
            INSERT INTO addresses(account_id,recipient_name,street,city,department,phone)
            VALUES (%d,'Ana CU09','Calle CU09','Bogotá','Bogotá','3001234567'); SET @d=LAST_INSERT_ID();
            INSERT INTO products(name,price,stock,category,active,store_id) VALUES ('Fixture CU09',100,5,'Hogar',TRUE,1); SET @p=LAST_INSERT_ID();
            INSERT INTO orders(account_id,status,total,address_id,shipping_method,transaction_id,created_at,store_id,payment_status,
                delivery_recipient_name,delivery_street,delivery_city,delivery_department,delivery_phone,discount_amount,shipping_cost,seller_name_snapshot)
            VALUES (%d,'%s',100,@d,'STANDARD',UUID(),NOW(),1,'APPROVED','Ana CU09','Calle CU09','Bogotá','Bogotá','3001234567',0,0,'Tienda'); SET @o=LAST_INSERT_ID();
            INSERT INTO order_items(order_id,product_id,product_name,quantity,unit_price,subtotal,unit_cost) VALUES (@o,@p,'Fixture CU09',1,100,100,30);
            INSERT INTO order_status_history(order_id,from_status,to_status,actor_type,reason,correlation_id,created_at)
            VALUES (@o,NULL,'%s','SYSTEM','Fixture CU09 sin pago','cu09',NOW());
            COMMIT; SELECT @o;
            """.formatted(a,a,state,state)));
    }
    static void obligation(String email,long order,String code) throws Exception {
        long a=id(email);
        String query=switch(code) {
            case "ORDERS" -> "UPDATE orders SET status='CONFIRMED' WHERE id="+order;
            case "PAYMENTS" -> "UPDATE orders SET payment_status='REFUND_PENDING' WHERE id="+order;
            case "REFUNDS" -> "INSERT INTO refunds(order_id,amount,idempotency_key,status,correlation_id,created_at,updated_at) VALUES ("+order+",100,UUID(),'PENDING','cu09',NOW(),NOW())";
            case "CLAIMS" -> "INSERT INTO claims(order_id,product_id,product_name,item_total,buyer_account_id,store_id,description,status,created_at,updated_at) SELECT order_id,product_id,'Fixture CU09',100,"+a+",1,'Fixture CU09','OPEN',NOW(),NOW() FROM order_items WHERE order_id="+order;
            case "RETURNS" -> "INSERT INTO return_requests(order_id,order_item_id,product_id,product_name,quantity,unit_price,refund_amount,buyer_account_id,store_id,status,reason_code,description,created_at,updated_at) SELECT order_id,id,product_id,product_name,quantity,unit_price,100,"+a+",1,'REQUESTED','OTHER','Fixture CU09',NOW(),NOW() FROM order_items WHERE order_id="+order;
            case "CHECKOUTS", "EXTERNAL_PAYMENTS", "EXTERNAL_REFUNDS" -> "SET @c=UUID(); INSERT INTO payment_checkouts(id,account_id,snapshot_json,total,status,expires_at,created_at,updated_at,recovery_next_at,financial_discovery_next_at) VALUES (@c,"+a+",'{}',100,'"+(code.equals("CHECKOUTS")?"REVIEW":"COMPLETED")+"',NOW()+INTERVAL 1 HOUR,NOW(),NOW(),NOW()+INTERVAL 1 YEAR,NOW()+INTERVAL 1 YEAR);"
                +(code.equals("CHECKOUTS")?"":"SET @payment=CONCAT('CU09-',UUID()); INSERT INTO external_payment_observations(payment_id,checkout_id,verified_status,amount,currency,collector_id,resolution_status,first_verified_at,last_verified_at) VALUES (@payment,@c,'approved',100,'COP','cu09-fixture','"+(code.equals("EXTERNAL_PAYMENTS")?"REQUIRES_REVIEW":"RESOLVED")+"',NOW(),NOW());")
                +(code.equals("EXTERNAL_REFUNDS")?"INSERT INTO external_payment_refunds(payment_id,idempotency_key,amount,reason,status,next_attempt_at) VALUES (@payment,UUID(),100,'CU09_FIXTURE','INTENT',NOW()+INTERVAL 1 YEAR);":"");
            case "RESERVATIONS" -> "INSERT INTO inventory_reservations(product_id,account_id,quantity,status,created_at,expires_at) SELECT product_id,"+a+",1,'ACTIVE',NOW(),NOW()+INTERVAL 1 HOUR FROM order_items WHERE order_id="+order;
            case "STORE_INFORMATION" -> "INSERT INTO stores(name,description,owner_account_id,status) VALUES ('Tienda CU09','Fixture CU09',"+a+",'ACTIVE'); SET @s=LAST_INSERT_ID(); INSERT INTO store_clarification_requests(store_id,requested_by_account_id,message,requested_at,due_at,status) VALUES (@s,"+a+",'Aclaración CU09',NOW(),NOW()+INTERVAL 1 DAY,'OPEN')";
            default -> throw new IllegalArgumentException(code);
        };sql(query+";");
    }
}
