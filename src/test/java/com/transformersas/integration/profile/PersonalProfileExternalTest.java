package com.transformersas.integration.profile;

import com.transformersas.integration.support.MarketplaceClient;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.transformersas.integration.profile.ProfileFixture.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contratos externos CU-09 sobre servidor, autenticación, MySQL y SMTP reales. */
class PersonalProfileExternalTest {
    @Test void consultaAisladaYPerfilUnicoEntreRoles() throws Exception {
        String a=account(),b=account();var c=login(a);var other=login(b);
        c.get(PATH).then().statusCode(200).body("email",is(a),"fullName",is("Ana Perfil"),"essentialCommunications",is(true));
        var update=body(c);update.put("fullName","Sofía CU09");c.put(PATH,json(update)).then().statusCode(200);
        other.get(PATH).then().statusCode(200).body("email",is(b),"fullName",is("Ana Perfil"));
        c.put("/api/auth/active-role",json(Map.of("role","VENDEDOR"))).then().statusCode(200);c.renewCsrf();
        c.get(PATH).then().statusCode(200).body("fullName",is("Sofía CU09"));
        assertEquals("1",sql("SELECT COUNT(*) FROM account_profiles WHERE account_id="+id(a)+";"));
    }
    @Test void anonimoNoAccedeNiModifica() {
        var c=MarketplaceClient.visitor();c.get(PATH).then().statusCode(401);
        c.get(PATH+"/closure").then().statusCode(401);
        c.put(PATH,"{}").then().statusCode(401);
        for(String endpoint:List.of("/email/resend","/email/confirm","/closure")) c.post(PATH+endpoint,"{}").then().statusCode(401);
    }
    @Test void datosValidosPersistenEnOtraSesion() throws Exception {
        String email=account();var c=login(email);var b=body(c);
        b.put("fullName","Sofía Actualizada");b.put("birthDate","1998-02-03");b.put("phone","+57 300 7654321");
        c.put(PATH,json(b)).then().statusCode(200).body("profile.version",is(1));
        login(email).get(PATH).then().statusCode(200).body("fullName",is("Sofía Actualizada"),"birthDate",is("1998-02-03"),"phone",is("+57 300 7654321"));
    }
    @ParameterizedTest @ValueSource(strings={"fullName","birthDate","phone","email","confirmChanges","version"})
    void validacionesNoAlteranPerfil(String field) throws Exception {
        var c=login(account());var b=body(c);
        b.put(field,switch(field){case "birthDate"->"2999-01-01";case "confirmChanges"->false;case "version"->-1;default->"x";});
        if(field.equals("fullName")) b.put(field," ");
        c.put(PATH,json(b)).then().statusCode(400);
        c.get(PATH).then().statusCode(200).body("version",is(0),"fullName",is("Ana Perfil"),"pendingEmail",nullValue());
    }
    @Test void preferenciasIndependientesSinClaveYEsencialesActivas() throws Exception {
        String email=account();var c=login(email);
        for(boolean[] values:List.of(new boolean[]{true,false},new boolean[]{false,true},new boolean[]{false,false})) {
            var b=body(c);b.put("promotions",values[0]);b.put("marketplaceNews",values[1]);b.put("currentPassword","");
            c.put(PATH,json(b)).then().statusCode(200);
            login(email).get(PATH).then().statusCode(200).body("promotions",is(values[0]),"marketplaceNews",is(values[1]),"essentialCommunications",is(true));
        }
        var b=body(c);b.put("essentialCommunications",false);c.put(PATH,json(b)).then().statusCode(400);
        c.get(PATH).then().body("essentialCommunications",is(true),"version",is(3));
    }
    @Test void identidadAjenaYCsrfNoPermitenCambios() throws Exception {
        String a=account(),b=account();var c=login(a);var update=body(c);
        update.put("accountId",id(b));c.put(PATH,json(update)).then().statusCode(400);
        update.remove("accountId");update.put("fullName","Cambio prohibido");
        c.withoutCsrf("PUT",PATH,json(update)).then().statusCode(403);
        c.withoutCsrf("POST",PATH+"/closure",close(c)).then().statusCode(403);
        for(String endpoint:List.of("/email/resend","/email/confirm")) c.withoutCsrf("POST",PATH+endpoint,"{}").then().statusCode(403);
        c.get(PATH).then().body("version",is(0));login(b).get(PATH).then().body("version",is(0));
    }
    @Test void claveIncorrectaYCorreoOcupadoNoCambianDatos() throws Exception {
        var c=login(account());var b=body(c);b.put("fullName","No guardar");b.put("currentPassword","incorrecta");
        c.put(PATH,json(b)).then().statusCode(403).body("code",is("CURRENT_PASSWORD_INVALID"));
        b.put("currentPassword",PASSWORD);b.put("email",account());
        c.put(PATH,json(b)).then().statusCode(409).body("code",is("EMAIL_ALREADY_REGISTERED"));
        c.get(PATH).then().body("fullName",is("Ana Perfil"),"version",is(0),"pendingEmail",nullValue());
    }
    @Test void correoPendienteReenvioCaducidadConfirmacionYSesiones() throws Exception {
        String email=account(),next="cu09-"+UUID.randomUUID()+"@example.test";
        var c=login(email);var second=login(email);String token=requestEmail(c,next);
        c.get(PATH).then().body("email",is(email),"pendingEmail",is(next),"pendingEmailExpiresAt",notNullValue());
        MarketplaceClient.visitor().authenticate(next,PASSWORD).then().statusCode(401);
        var stranger=login(account());stranger.post(PATH+"/email/confirm",json(Map.of("token",token))).then().statusCode(400);
        c.post(PATH+"/email/confirm",json(Map.of("token","incorrecto"))).then().statusCode(400).body("code",is("INVALID_EMAIL_CHANGE_TOKEN"));
        c.post(PATH+"/email/resend",json(Map.of("currentPassword","incorrecta"))).then().statusCode(403);
        c.post(PATH+"/email/resend",json(Map.of("currentPassword",PASSWORD))).then().statusCode(200).body("verificationDeliveryFailed",is(false));
        String fresh=mail(next,token);assertNotEquals(token,fresh);
        c.post(PATH+"/email/confirm",json(Map.of("token",token))).then().statusCode(400);
        sql("UPDATE account_email_changes SET expires_at=NOW()-INTERVAL 1 MINUTE WHERE account_id="+id(email)+";");
        c.post(PATH+"/email/confirm",json(Map.of("token",fresh))).then().statusCode(400);
        c.get(PATH).then().body("email",is(email));
        c.post(PATH+"/email/resend",json(Map.of("currentPassword",PASSWORD))).then().statusCode(200);
        String valid=mail(next,token,fresh);
        // Se excluyen todos los códigos previos al seleccionar el correo más reciente.
        c.post(PATH+"/email/confirm",json(Map.of("token",valid))).then().statusCode(204);
        c.get(PATH).then().statusCode(401);second.get(PATH).then().statusCode(401);
        MarketplaceClient.visitor().authenticate(email,PASSWORD).then().statusCode(401);
        var renewed=login(next);renewed.get(PATH).then().body("email",is(next),"pendingEmail",nullValue());
        renewed.post(PATH+"/email/confirm",json(Map.of("token",valid))).then().statusCode(400);
    }
    @Test void versionObsoletaNoSobrescribe() throws Exception {
        var c=login(account());var b=body(c);var other=new LinkedHashMap<>(b);b.put("promotions",true);
        c.put(PATH,json(b)).then().statusCode(200);
        c.put(PATH,json(other)).then().statusCode(409).body("code",is("PROFILE_CHANGED"));
        c.get(PATH).then().body("promotions",is(true),"version",is(1));
    }
    @ParameterizedTest @ValueSource(strings={"ORDERS","PAYMENTS","REFUNDS","CLAIMS","RETURNS","CHECKOUTS","EXTERNAL_PAYMENTS","EXTERNAL_REFUNDS","RESERVATIONS","STORE_INFORMATION"})
    void obligacionesBloqueanCierre(String code) throws Exception {
        String email=account();var c=login(email);long order=order(email,"DELIVERED");obligation(email,order,code);
        c.get(PATH+"/closure").then().statusCode(200).body("allowed",is(false),"blockers.code",hasItem(code));
        c.post(PATH+"/closure",close(c)).then().statusCode(409).body("code",is("ACCOUNT_HAS_PENDING_PROCESSES"));
        c.get(PATH).then().statusCode(200);assertEquals("ACTIVA",sql("SELECT status FROM user_accounts WHERE id="+id(email)+";"));
    }
    @Test void cierreExigeClaveConsentimientoYVersionVigente() throws Exception {
        var c=login(account());var b=new LinkedHashMap<String,Object>(Map.of("currentPassword",PASSWORD,"version",0,"confirmClosure",false));
        c.post(PATH+"/closure",json(b)).then().statusCode(400);
        b.put("confirmClosure",true);b.put("currentPassword","incorrecta");c.post(PATH+"/closure",json(b)).then().statusCode(403);
        b.put("currentPassword",PASSWORD);b.put("version",1);c.post(PATH+"/closure",json(b)).then().statusCode(409);
        c.get(PATH).then().statusCode(200);
    }
    @Test void cierreConservaHistorialYRevocaTodasLasSesiones() throws Exception {
        String email=account();long account=id(email),order=order(email,"DELIVERED");var c=login(email);var second=login(email);
        long store=Long.parseLong(sql("INSERT INTO stores(name,owner_account_id,status) VALUES ('Tienda histórica CU09',"+account+",'ACTIVE'); SELECT LAST_INSERT_ID();"));
        sql("UPDATE orders SET store_id="+store+" WHERE id="+order+"; UPDATE products SET store_id="+store+" WHERE id IN (SELECT product_id FROM order_items WHERE order_id="+order+");");
        obligation(email,order,"REFUNDS");obligation(email,order,"CLAIMS");obligation(email,order,"RETURNS");
        sql("UPDATE refunds SET status='COMPLETED' WHERE order_id="+order+"; UPDATE claims SET status='RESOLVED' WHERE order_id="+order+"; UPDATE return_requests SET status='FINISHED' WHERE order_id="+order+";");
        String query="SELECT CONCAT((SELECT COUNT(*) FROM orders WHERE id="+order+"),':',(SELECT COUNT(*) FROM order_items WHERE order_id="+order+"),':',(SELECT COUNT(*) FROM order_status_history WHERE order_id="+order+"),':',(SELECT COUNT(*) FROM refunds WHERE order_id="+order+"),':',(SELECT COUNT(*) FROM claims WHERE order_id="+order+"),':',(SELECT COUNT(*) FROM return_requests WHERE order_id="+order+"));";
        String before=sql(query);assertEquals("1:1:1:1:1:1",before);
        c.get(PATH+"/closure").then().statusCode(200).body("allowed",is(true),"blockers",empty(),"consequences",containsString("Se conservarán pedidos"));
        c.post(PATH+"/closure",close(c)).then().statusCode(204);
        c.get(PATH).then().statusCode(401);second.get(PATH).then().statusCode(401);
        MarketplaceClient.visitor().authenticate(email,PASSWORD).then().statusCode(401);
        assertEquals(before,sql(query));
        assertEquals("SUSPENDED",sql("SELECT status FROM stores WHERE id="+store+";"));
        assertEquals("0:PAUSED",sql("SELECT CONCAT(active,':',status) FROM products WHERE id IN (SELECT product_id FROM order_items WHERE order_id="+order+");"));
        assertEquals("INACTIVA",sql("SELECT status FROM user_accounts WHERE id="+account+";"));
        assertEquals("1",sql("SELECT COUNT(*) FROM account_profiles WHERE account_id="+account+";"));
    }
}
