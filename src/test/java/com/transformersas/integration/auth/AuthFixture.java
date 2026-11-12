package com.transformersas.integration.auth;

import com.transformersas.integration.support.MarketplaceClient;
import groovy.json.JsonOutput;
import java.util.*;
import java.util.regex.Pattern;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

final class AuthFixture {
    static final String PASSWORD="Cu08RealTestPassword!";
    static final String NEW_PASSWORD="Cu08NewExternalPassword!";
    static String json(Map<String,?> value) { return JsonOutput.toJson(value); }
    static String sql(String query) throws Exception {
        Process p=new ProcessBuilder("bash","scripts/cu08-compose.sh","exec","-T","mysql","sh","-c",
            "MYSQL_PWD=\"$MYSQL_PASSWORD\" exec mysql --default-character-set=utf8mb4 -N -B -u \"$MYSQL_USER\" \"$MYSQL_DATABASE\"").start();
        try(var out=p.getOutputStream()) { out.write(query.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        String result=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0,p.waitFor(),"Isolated fixture SQL failed");return result;
    }
    static String account(boolean verified, boolean seller) throws Exception {
        assertEquals("cu08_e2e",sql("SELECT DATABASE();"));
        String email="cu08-"+UUID.randomUUID()+"@example.test";
        sql("INSERT INTO user_accounts(email,password_hash,status,email_verified_at) VALUES ('"+email+"','$2y$12$1b8lQufQ.HVnswuPxAClJ.mZciYOAGr8F2XGKjzndmAJQ7t94AK7m','ACTIVA',"+(verified?"CURRENT_TIMESTAMP(6)":"NULL")+");"
            +"INSERT INTO user_account_roles(account_id,role) SELECT id,'COMPRADOR' FROM user_accounts WHERE email='"+email+"';"
            +(seller?"INSERT INTO user_account_roles(account_id,role) SELECT id,'VENDEDOR' FROM user_accounts WHERE email='"+email+"';":""));
        return email;
    }
    static MarketplaceClient login(String email) {
        var c=MarketplaceClient.visitor(); c.authenticate(email,PASSWORD).then().statusCode(204);return c;
    }
    static String token(String email,String subject,String except) throws Exception {
        String url=System.getenv("CU08_MAIL_URL");
        for(int i=0;i<60;i++) {
            var r=given().get(url+"/api/v1/messages");r.then().statusCode(200);
            for(Map<String,Object> m:r.jsonPath().<Map<String,Object>>getList("messages")) {
                if(!m.get("To").toString().contains(email)||!m.get("Subject").toString().contains(subject)) continue;
                var detail=given().get(url+"/api/v1/message/"+m.get("ID"));detail.then().statusCode(200);
                var match=Pattern.compile("(?:^|\\s)([A-Za-z0-9_-]{43})(?=\\s|$)",Pattern.MULTILINE).matcher(detail.jsonPath().getString("Text"));
                if(match.find()&&!match.group(1).equals(except)) return match.group(1);
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Expected test mail was not delivered (mail contents redacted)");
    }
    static String ownId(MarketplaceClient c) {
        var r=c.get("/api/auth/sessions");r.then().statusCode(200);
        return r.jsonPath().getString("find { it.current }.id");
    }
}
