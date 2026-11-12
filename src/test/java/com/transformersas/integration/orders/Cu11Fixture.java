package com.transformersas.integration.orders;

import io.restassured.path.json.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Fixtures and observation only in cu11_e2e; all behavior under test goes through HTTP. */
final class Cu11Fixture {
    static final String PASSWORD="Cu11ExternalPassword123!";
    record Order(String email,long accountId,long orderId,long productId,long addressId,String transactionId) { }
    static String execute(String... arguments) throws Exception {
        var command=new ArrayList<>(List.of("python3","scripts/cu11-fixture.py"));command.addAll(List.of(arguments));
        Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
        if(!p.waitFor(75,TimeUnit.SECONDS)) {p.destroyForcibly();throw new AssertionError("CU11 fixture timed out");}
        String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
        assertEquals(0,p.exitValue(),text);return text;
    }
    static Order create(String... arguments) throws Exception {
        var j=new JsonPath(execute(arguments));
        return new Order(j.getString("email"),j.getLong("accountId"),j.getLong("orderId"),j.getLong("productId"),j.getLong("addressId"),j.getString("transactionId"));
    }
    static String sql(String query) throws Exception {return execute("--sql",query);}
    static String path(Order order) {return "/api/orders/"+order.orderId();}
    static String cancel(Order order) {return path(order)+"/cancellation";}
    static final String REASON="{\"reasonCode\":\"OTHER\",\"details\":\"  Fixture de cancelación CU11  \"}";
    private Cu11Fixture() { }
}
