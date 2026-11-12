package com.transformersas.integration.buyers;

import com.transformersas.integration.support.MarketplaceClient;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** External HTTP only; SQL is restricted to observation/fault fixtures in our disposable Compose. */
@EnabledIfEnvironmentVariable(named="CU07_ISOLATED", matches="true")
class BuyerRegistrationIntegrationTest {
    static final String PASSWORD="Cu07ExternalPassword123!";
    static final String CONSENT="{\"acceptTerms\":true,\"acceptPrivacy\":true}";
    static String email() { return "external-"+UUID.randomUUID()+"@example.test"; }
    static String body(String email) {
        return """
          {"fullName":"Ana Pérez","birthDate":"2000-05-20","email":"%s",
           "phone":"+57 300 123 4567","password":"%s","passwordConfirmation":"%s",
           "acceptTerms":true,"acceptPrivacy":true}
          """.formatted(email,PASSWORD,PASSWORD);
    }
    static Response register(String email) { return MarketplaceClient.visitor().post("/api/buyers/register",body(email)); }
    static String sql(String query) throws Exception {
        Process p=new ProcessBuilder("bash","scripts/cu07-compose.sh","exec","-T","mysql","sh","-c",
            "MYSQL_PWD=\"$MYSQL_PASSWORD\" exec mysql --default-character-set=utf8mb4 -N -B -u \"$MYSQL_USER\" \"$MYSQL_DATABASE\"").start();
        try(var out=p.getOutputStream()) { out.write(query.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        String result=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0,p.waitFor(),"Isolated fixture SQL failed"); return result;
    }
    static void compose(String action) throws Exception {
        Process p=new ProcessBuilder("bash","scripts/cu07-compose.sh",action,"mailpit").redirectErrorStream(true).start();
        p.getInputStream().readAllBytes(); assertEquals(0,p.waitFor());
    }
    static String token(String email, String except) throws Exception {
        String mail=System.getenv().getOrDefault("CU07_MAIL_URL","http://127.0.0.1:18025");
        for(int i=0;i<60;i++) {
            var messages=given().get(mail+"/api/v1/messages"); messages.then().statusCode(200);
            for(Map<String,Object> m: messages.jsonPath().<Map<String,Object>>getList("messages")) {
                if(!m.get("To").toString().contains(email)) continue;
                String text=given().get(mail+"/api/v1/message/"+m.get("ID")).jsonPath().getString("Text");
                var match=Pattern.compile("(?:^|\\s)([A-Za-z0-9_-]{43})(?=\\s|$)",Pattern.MULTILINE).matcher(text);
                if(match.find() && !match.group(1).equals(except)) return match.group(1);
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No verification code delivered to isolated mailbox");
    }
    static void confirm(String token,int status) {
        MarketplaceClient.visitor().post("/api/auth/email-verification/confirm","{\"token\":\""+token+"\"}").then().statusCode(status);
    }
    static MarketplaceClient verified(String email) throws Exception {
        register(email).then().statusCode(201); confirm(token(email,""),204);
        var client=MarketplaceClient.visitor(); client.authenticate(email,PASSWORD).then().statusCode(204); return client;
    }
    @BeforeAll static void contract() throws Exception {
        assertTrue(Set.of("127.0.0.1","localhost").contains(java.net.URI.create(System.getenv("BACKEND_BASE_URL")).getHost()), "CU07 requires the isolated loopback backend");
        assertEquals("cu08_e2e",sql("SELECT DATABASE();"));
        var terms=MarketplaceClient.anonymous().get("/api/buyers/terms"); terms.then().statusCode(200);
        for(String key:List.of("termsVersion","terms","privacyVersion","privacy")) assertFalse(terms.jsonPath().getString(key).isBlank());
    }
    @Test void registrationPersistsOnlyBuyerAndProfile() throws Exception {
        String e=email(); var r=register(e); r.then().statusCode(201);
        assertTrue(r.jsonPath().getBoolean("emailVerificationRequired")); assertFalse(r.jsonPath().getBoolean("verificationDeliveryFailed"));
        assertEquals("Ana Pérez\t2000-05-20\t+57 300 123 4567",sql("SELECT p.full_name,p.birth_date,p.phone FROM account_profiles p JOIN user_accounts a ON a.id=p.account_id WHERE a.email='"+e+"';"));
        assertEquals("COMPRADOR",sql("SELECT r.role FROM user_account_roles r JOIN user_accounts a ON a.id=r.account_id WHERE a.email='"+e+"';"));
        assertEquals("1",sql("SELECT COUNT(*) FROM buyer_consents c JOIN user_accounts a ON a.id=c.account_id WHERE a.email='"+e+"';"));
        assertEquals("0",sql("SELECT COUNT(*) FROM stores s JOIN user_accounts a ON a.id=s.owner_account_id WHERE a.email='"+e+"';"));
        var visitor=MarketplaceClient.visitor(); visitor.get("/api/auth/me").then().statusCode(401);
        visitor.authenticate(e,PASSWORD).then().statusCode(403);
        confirm(token(e,""),204); visitor.authenticate(e,PASSWORD).then().statusCode(204);
        assertEquals(List.of("COMPRADOR"),visitor.get("/api/auth/me").jsonPath().getList("roles"));
        visitor.get("/api/orders").then().statusCode(200);
        visitor.put("/api/auth/active-role","{\"role\":\"VENDEDOR\"}").then().statusCode(403);
    }
    @ParameterizedTest @CsvSource({"fullName,''","birthDate,2999-01-01","email,invalid","phone,abc","password,short","passwordConfirmation,different","acceptTerms,false","acceptPrivacy,false"})
    void rejectsInvalidData(String field,String value) throws Exception {
        String e=email(); String json=body(e);
        if(field.startsWith("accept")) json=json.replace("\""+field+"\":true","\""+field+"\":false");
        else json=json.replaceAll("\""+field+"\":\"[^\"]*\"","\""+field+"\":\""+value+"\"");
        MarketplaceClient.visitor().post("/api/buyers/register",json).then().statusCode(400);
        assertEquals("0",sql("SELECT COUNT(*) FROM user_accounts WHERE email='"+e+"';"));
    }
    @Test void duplicateAndConcurrentRequestsHaveOneIdentity() throws Exception {
        String e=email(); var a=MarketplaceClient.visitor(); var b=MarketplaceClient.visitor();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var x=pool.submit(()->{start.await();return a.post("/api/buyers/register",body(e)).statusCode();});
            var y=pool.submit(()->{start.await();return b.post("/api/buyers/register",body(e)).statusCode();});
            start.countDown(); assertEquals(List.of(201,409),java.util.stream.Stream.of(x.get(),y.get()).sorted().toList());
        }
        var duplicate=register(e); duplicate.then().statusCode(409);
        assertEquals("ACCOUNT_ACCESS_REQUIRED",duplicate.jsonPath().getString("code"));
        assertEquals("1",sql("SELECT COUNT(*) FROM user_accounts WHERE email='"+e+"';"));
        assertFalse(duplicate.asString().contains("passwordHash"));
    }
    @Test void verificationRejectsInvalidReplacedUsedAndExpiredTokens() throws Exception {
        String e=email(); register(e).then().statusCode(201); String old=token(e,"");
        confirm("invalid",400);
        MarketplaceClient.visitor().post("/api/auth/email-verification/resend","{\"email\":\""+e+"\",\"password\":\""+PASSWORD+"\"}").then().statusCode(202);
        String fresh=token(e,old); confirm(old,400); confirm(fresh,204); confirm(fresh,400);
        String expired=email(); register(expired).then().statusCode(201); String code=token(expired,"");
        sql("UPDATE email_verification_tokens t JOIN user_accounts a ON a.id=t.account_id SET t.expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE a.email='"+expired+"';");
        confirm(code,400);
    }
    @Test void smtpFailureIsRecoverable() throws Exception {
        String e=email();
        try { compose("stop"); var r=register(e); r.then().statusCode(201); assertTrue(r.jsonPath().getBoolean("verificationDeliveryFailed")); }
        finally { compose("start"); }
        MarketplaceClient.visitor().authenticate(e,PASSWORD).then().statusCode(403);
        MarketplaceClient.visitor().post("/api/auth/email-verification/resend","{\"email\":\""+e+"\",\"password\":\""+PASSWORD+"\"}").then().statusCode(202);
        confirm(token(e,""),204); MarketplaceClient.visitor().authenticate(e,PASSWORD).then().statusCode(204);
    }
    @Test void enableUsesOwnIdentityPreservesRolesAndIsIdempotent() throws Exception {
        String seller=email(); String other=email(); var buyer=verified(other); long otherId=buyer.get("/api/auth/me").jsonPath().getLong("accountId");
        // Fixture is created through public registration and verification, then given the legacy seller-only state.
        var c=verified(seller);
        sql("UPDATE user_account_roles r JOIN user_accounts a ON a.id=r.account_id SET r.role='VENDEDOR' WHERE a.email='"+seller+"';");
        String snapshot=sql("SELECT a.password_hash,a.email_verified_at,p.full_name,p.birth_date,p.phone FROM user_accounts a JOIN account_profiles p ON p.account_id=a.id WHERE a.email='"+seller+"';");
        c=MarketplaceClient.loginAs(seller,PASSWORD,"VENDEDOR"); var before=c.get("/api/auth/me").jsonPath();
        c.post("/api/buyers/enable","{\"acceptTerms\":true,\"acceptPrivacy\":true,\"accountId\":"+otherId+"}").then().statusCode(400);
        c.post("/api/buyers/enable","{\"acceptTerms\":false,\"acceptPrivacy\":true}").then().statusCode(400);
        var r=c.post("/api/buyers/enable",CONSENT); r.then().statusCode(200); assertTrue(r.jsonPath().getBoolean("buyerRoleAdded"));
        var after=c.get("/api/auth/me").jsonPath(); assertEquals(before.getLong("accountId"),after.getLong("accountId"));
        assertEquals("VENDEDOR",after.getString("activeRole")); assertEquals(Set.of("VENDEDOR","COMPRADOR"),new HashSet<>(after.getList("roles")));
        assertFalse(c.post("/api/buyers/enable",CONSENT).jsonPath().getBoolean("buyerRoleAdded"));
        assertEquals(List.of("COMPRADOR"),buyer.get("/api/auth/me").jsonPath().getList("roles"));
        c.put("/api/auth/active-role","{\"role\":\"COMPRADOR\"}").then().statusCode(200); c.renewCsrf(); c.get("/api/orders").then().statusCode(200);
        assertEquals("1",sql("SELECT COUNT(*) FROM user_accounts WHERE email='"+seller+"';"));
        assertEquals(snapshot,sql("SELECT a.password_hash,a.email_verified_at,p.full_name,p.birth_date,p.phone FROM user_accounts a JOIN account_profiles p ON p.account_id=a.id WHERE a.email='"+seller+"';"));
        assertEquals("2",sql("SELECT COUNT(*) FROM user_account_roles r JOIN user_accounts a ON a.id=r.account_id WHERE a.email='"+seller+"';"));
        MarketplaceClient.visitor().authenticate(seller,PASSWORD).then().statusCode(204);
    }
    @Test void buyerIsolationAndSellerRegistrationRegression() throws Exception {
        var first=verified(email()); var second=verified(email());
        var address=first.post("/api/addresses", """
          {"recipientName":"Ana Prueba","street":"Calle 123","city":"Bogotá",
          "department":"Cundinamarca","postalCode":"110111","phone":"3000000000"}
          """);
        address.then().statusCode(201); long id=address.jsonPath().getLong("id");
        assertTrue(first.get("/api/addresses").jsonPath().getList("id",Long.class).contains(id));
        assertFalse(second.get("/api/addresses").jsonPath().getList("id",Long.class).contains(id));
        assertEquals(List.of(),second.get("/api/orders").jsonPath().getList("$"));
        String e=email();
        var seller=MarketplaceClient.visitor().post("/api/sellers/register",
            "{\"email\":\""+e+"\",\"password\":\""+PASSWORD+"\",\"storeName\":\"Shop "+UUID.randomUUID()+"\",\"acceptTerms\":true}");
        seller.then().statusCode(201); assertFalse(seller.jsonPath().getBoolean("sellerRoleActive"));
        confirm(token(e,""),204);
        var session=MarketplaceClient.loginAs(e,PASSWORD,"VENDEDOR");
        assertEquals(Set.of("COMPRADOR","VENDEDOR"),new HashSet<>(session.get("/api/auth/me").jsonPath().getList("roles")));
        session.post("/api/auth/logout").then().statusCode(204);
        session.get("/api/auth/me").then().statusCode(401);
    }
    @Test void missingAndUnknownFieldsAndUtf8PasswordLimitAreRejected() {
        MarketplaceClient.visitor().post("/api/buyers/register","{}").then().statusCode(400);
        MarketplaceClient.visitor().post("/api/buyers/register",body(email()).replace("{","{\"roles\":[\"VENDEDOR\"],")).then().statusCode(400);
        String tooLong="é".repeat(37);
        MarketplaceClient.visitor().post("/api/buyers/register",body(email()).replace(PASSWORD,tooLong)).then().statusCode(400);
    }
    @Test void csrfAndAnonymousEnableAreEnforced() {
        MarketplaceClient.visitor().post("/api/buyers/enable",CONSENT).then().statusCode(401);
        MarketplaceClient.anonymous().post("/api/buyers/register",body(email())).then().statusCode(403);
    }
}
