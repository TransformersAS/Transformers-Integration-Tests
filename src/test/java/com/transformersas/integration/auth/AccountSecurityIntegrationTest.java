package com.transformersas.integration.auth;

import com.transformersas.integration.support.MarketplaceClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.transformersas.integration.auth.AuthFixture.*;

@EnabledIfEnvironmentVariable(named="CU08_ISOLATED",matches="true")
class AccountSecurityIntegrationTest {
    @BeforeAll static void isolated() throws Exception {
        assertTrue(Set.of("127.0.0.1","localhost").contains(java.net.URI.create(System.getenv("BACKEND_BASE_URL")).getHost()));
        assertEquals("cu08_e2e",sql("SELECT DATABASE();"));
        assertNotNull(System.getenv("CU08_OBSERVED_ORIGIN"),"Run the verified-proxy CU08 harness");
    }
    @Test void credentialsAccountRolesAndLogout() throws Exception {
        String e=account(true,true);var c=MarketplaceClient.visitor();
        c.authenticate(e,"WrongPassword123!").then().statusCode(401);
        c.get("/api/auth/me").then().statusCode(401);
        c.authenticate(e,PASSWORD).then().statusCode(204);
        var me=c.get("/api/auth/me");me.then().statusCode(200);
        assertEquals(e,me.jsonPath().getString("email"));assertTrue(me.jsonPath().getLong("accountId")>0);
        assertEquals(Set.of("COMPRADOR","VENDEDOR"),new HashSet<>(me.jsonPath().getList("roles")));
        assertFalse(me.asString().contains("password"));
        for(String role:List.of("VENDEDOR","COMPRADOR")) {
            c.put("/api/auth/active-role",json(Map.of("role",role))).then().statusCode(200);c.renewCsrf();
            assertEquals(role,c.get("/api/auth/me").jsonPath().getString("activeRole"));
            c.get("/api/auth/validation/"+role.toLowerCase(Locale.ROOT)).then().statusCode(204);
        }
        c.put("/api/auth/active-role",json(Map.of("role","ADMIN"))).then().statusCode(403);
        c.put("/api/auth/active-role","{}").then().statusCode(400);
        assertEquals("COMPRADOR",c.get("/api/auth/me").jsonPath().getString("activeRole"));
        c.post("/api/auth/logout").then().statusCode(204);
        c.get("/api/auth/me").then().statusCode(401); // replays the previous real SESSION cookie
    }
    @Test void sessionOwnershipSpecificAndOtherRevocations() throws Exception {
        String e=account(true,false);var a=login(e);var b=login(e);var stranger=login(account(true,false));
        String aid=ownId(a),bid=ownId(b),sid=ownId(stranger);
        assertNotEquals(aid,bid);assertTrue(aid.matches("[a-f0-9]{64}"));
        var list=a.get("/api/auth/sessions");list.then().statusCode(200);
        assertEquals(Set.of(aid,bid),new HashSet<>(list.jsonPath().getList("id")));
        assertFalse(list.jsonPath().getList("id").contains(sid));
        a.delete("/api/auth/sessions/"+sid).then().statusCode(404);
        stranger.get("/api/auth/me").then().statusCode(200);
        a.delete("/api/auth/sessions/"+bid).then().statusCode(204);
        b.get("/api/auth/me").then().statusCode(401);a.get("/api/auth/me").then().statusCode(200);
        a.delete("/api/auth/sessions/"+bid).then().statusCode(404);
        var c=login(e);var d=login(e);
        a.post("/api/auth/sessions/revoke-others").then().statusCode(204);
        c.get("/api/auth/me").then().statusCode(401);d.get("/api/auth/me").then().statusCode(401);
        a.get("/api/auth/me").then().statusCode(200);
        assertEquals(List.of(aid),a.get("/api/auth/sessions").jsonPath().getList("id"));
        a.delete("/api/auth/sessions/"+aid).then().statusCode(204);a.get("/api/auth/me").then().statusCode(401);
    }
    @Test void passwordChangePreservesCurrentSessionAndRejectsOldCredentials() throws Exception {
        String e=account(true,false);var a=login(e);var other=login(e);
        a.put("/api/auth/password",json(Map.of("currentPassword","WrongPassword123!","newPassword",NEW_PASSWORD))).then().statusCode(403);
        a.put("/api/auth/password",json(Map.of("currentPassword",PASSWORD,"newPassword",PASSWORD))).then().statusCode(400);
        a.put("/api/auth/password",json(Map.of("currentPassword",PASSWORD,"newPassword","short"))).then().statusCode(400);
        other.get("/api/auth/me").then().statusCode(200);
        a.put("/api/auth/password",json(Map.of("currentPassword",PASSWORD,"newPassword",NEW_PASSWORD))).then().statusCode(204);
        a.get("/api/auth/me").then().statusCode(200);other.get("/api/auth/me").then().statusCode(401);
        MarketplaceClient.visitor().authenticate(e,PASSWORD).then().statusCode(401);
        MarketplaceClient.visitor().authenticate(e,NEW_PASSWORD).then().statusCode(204);
    }
    @Test void recoveryIsGenericReplacesTokensAndRevokesAllSessions() throws Exception {
        String e=account(true,false);var a=login(e);var b=login(e);var visitor=MarketplaceClient.visitor();
        var known=visitor.post("/api/auth/password-recovery/request",json(Map.of("email",e)));known.then().statusCode(202);
        var unknown=visitor.post("/api/auth/password-recovery/request",json(Map.of("email","absent-"+UUID.randomUUID()+"@example.test")));unknown.then().statusCode(202);
        assertEquals(known.asString(),unknown.asString());String old=token(e,"Recupera","");
        visitor.post("/api/auth/password-recovery/request",json(Map.of("email",e))).then().statusCode(202);
        String fresh=token(e,"Recupera",old);
        for(String invalid:List.of("bad",old)) visitor.post("/api/auth/password-recovery/confirm",json(Map.of("token",invalid,"newPassword",NEW_PASSWORD))).then().statusCode(400);
        visitor.post("/api/auth/password-recovery/confirm",json(Map.of("token",fresh,"newPassword","short"))).then().statusCode(400);
        visitor.post("/api/auth/password-recovery/confirm",json(Map.of("token",fresh,"newPassword",NEW_PASSWORD))).then().statusCode(204);
        a.get("/api/auth/me").then().statusCode(401);b.get("/api/auth/me").then().statusCode(401);
        visitor.post("/api/auth/password-recovery/confirm",json(Map.of("token",fresh,"newPassword",PASSWORD))).then().statusCode(400);
        MarketplaceClient.visitor().authenticate(e,PASSWORD).then().statusCode(401);
        MarketplaceClient.visitor().authenticate(e,NEW_PASSWORD).then().statusCode(204);
    }
    @Test void expiredRecoveryAndVerificationTokensAreRejected() throws Exception {
        String e=account(true,false);var v=MarketplaceClient.visitor();
        v.post("/api/auth/password-recovery/request",json(Map.of("email",e))).then().statusCode(202);
        String t=token(e,"Recupera","");
        sql("UPDATE password_recovery_tokens t JOIN user_accounts a ON a.id=t.account_id SET t.expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE a.email='"+e+"';");
        v.post("/api/auth/password-recovery/confirm",json(Map.of("token",t,"newPassword",NEW_PASSWORD))).then().statusCode(400);
        String pending=account(false,false);
        v.post("/api/auth/email-verification/resend",json(Map.of("email",pending,"password",PASSWORD))).then().statusCode(202);
        String verification=token(pending,"Verifica","");
        sql("UPDATE email_verification_tokens t JOIN user_accounts a ON a.id=t.account_id SET t.expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE a.email='"+pending+"';");
        v.post("/api/auth/email-verification/confirm",json(Map.of("token",verification))).then().statusCode(400);
    }
    @Test void emailVerificationRequiresCredentialsAndLatestUnusedToken() throws Exception {
        String e=account(false,false);var v=MarketplaceClient.visitor();
        var rejected=v.authenticate(e,PASSWORD);rejected.then().statusCode(403);assertEquals("EMAIL_NOT_VERIFIED",rejected.jsonPath().getString("code"));
        v.post("/api/auth/email-verification/resend",json(Map.of("email",e,"password","WrongPassword123!"))).then().statusCode(401);
        v.post("/api/auth/email-verification/resend",json(Map.of("email",e,"password",PASSWORD))).then().statusCode(202);
        String old=token(e,"Verifica","");
        v.post("/api/auth/email-verification/resend",json(Map.of("email",e,"password",PASSWORD))).then().statusCode(202);
        String fresh=token(e,"Verifica",old);
        for(String invalid:List.of("bad",old)) v.post("/api/auth/email-verification/confirm",json(Map.of("token",invalid))).then().statusCode(400);
        v.post("/api/auth/email-verification/confirm",json(Map.of("token",fresh))).then().statusCode(204);
        v.get("/api/auth/me").then().statusCode(401);
        v.post("/api/auth/email-verification/confirm",json(Map.of("token",fresh))).then().statusCode(400);
        v.authenticate(e,PASSWORD).then().statusCode(204);
    }
    @Test void loginTemporarilyLocksOnlyTheTargetAndRecoversAfterExpiry() throws Exception {
        String e=account(true,false);var v=MarketplaceClient.visitor();
        for(int i=0;i<5;i++) v.authenticate(e,"WrongPassword123!").then().statusCode(401);
        var blocked=v.authenticate(e,PASSWORD);blocked.then().statusCode(429);
        assertEquals("LOGIN_TEMPORARILY_UNAVAILABLE",blocked.jsonPath().getString("code"));
        long retry=Long.parseLong(blocked.header("Retry-After"));assertTrue(retry>0&&retry<=900);
        v.get("/api/auth/me").then().statusCode(401);login(account(true,false)).get("/api/auth/me").then().statusCode(200);
        String origin=System.getenv("CU08_OBSERVED_ORIGIN");assertTrue(origin.matches("[0-9a-fA-F:.]+"));
        sql("UPDATE login_attempts SET blocked_until=0 WHERE attempt_key=UNHEX(SHA2(CONCAT('"+e+"',CHAR(0),'"+origin+"'),256));");
        v.authenticate(e,PASSWORD).then().statusCode(204);
    }
    @Test void anonymousAndCsrfProtection() throws Exception {
        var v=MarketplaceClient.visitor();
        for(String path:List.of("/api/auth/me","/api/auth/sessions","/api/auth/validation/comprador")) v.get(path).then().statusCode(401);
        v.put("/api/auth/password",json(Map.of("currentPassword",PASSWORD,"newPassword",NEW_PASSWORD))).then().statusCode(401);
        v.put("/api/auth/active-role",json(Map.of("role","COMPRADOR"))).then().statusCode(401);
        v.post("/api/auth/sessions/revoke-others").then().statusCode(401);
        v.delete("/api/auth/sessions/unknown").then().statusCode(401);
        v.withoutCsrf("POST","/api/auth/password-recovery/request",json(Map.of("email","nobody@example.test"))).then().statusCode(403);
        var a=login(account(true,false));
        a.withoutCsrf("PUT","/api/auth/active-role",json(Map.of("role","COMPRADOR"))).then().statusCode(403);
        a.get("/api/auth/me").then().statusCode(200);
    }
}
