package com.transformersas.integration.orders;

import com.transformersas.integration.support.MarketplaceClient;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import com.transformersas.integration.orders.Cu11Fixture.Order;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static com.transformersas.integration.orders.Cu11Fixture.*;

@EnabledIfEnvironmentVariable(named="CU11_ISOLATED",matches="true")
class BuyerOrdersIntegrationTest {
    @BeforeAll static void isolated() throws Exception {
        assertEquals("cu11_e2e",sql("SELECT DATABASE();"));
        assertEquals("OK",System.getenv("CU11_REFUND_MODE"));
    }
    private MarketplaceClient buyer(Order order) {return MarketplaceClient.loginAs(order.email(),PASSWORD,"COMPRADOR");}
    private void unchanged(Order o,String status) throws Exception {
        assertEquals(status,sql("SELECT status FROM orders WHERE id="+o.orderId()));
        assertEquals("0",sql("SELECT COUNT(*) FROM order_cancellations WHERE order_id="+o.orderId()));
        assertEquals("0",sql("SELECT COUNT(*) FROM refunds WHERE order_id="+o.orderId()));
        assertEquals("8",sql("SELECT stock FROM products WHERE id="+o.productId()));
    }
    @Test void listContainsOnlyOwnOrdersInDescendingOrderAndIgnoresSpoofedIdentity() throws Exception {
        var old=create();var recent=create("--email",old.email(),"--created-at","2026-01-02T12:00:00");var foreign=create();
        var response=buyer(old).get("/api/orders?accountId="+foreign.accountId()+"&buyerId="+foreign.accountId());
        response.then().statusCode(200).body("id",equalTo(List.of((int)recent.orderId(),(int)old.orderId())));
        response.then().body("[0].total",equalTo(32000.0f)).body("[0].shippingMethod",equalTo("STANDARD"));
        assertFalse(response.asString().contains(foreign.email()));
    }
    @Test void detailReturnsHistoricalCommercialDataAndHidesInternalCost() throws Exception {
        var o=create();var r=buyer(o).get(path(o));r.then().statusCode(200)
            .body("id",equalTo((int)o.orderId())).body("status",equalTo("CONFIRMED"))
            .body("total",equalTo(32000.0f)).body("paymentStatus",equalTo("APPROVED"))
            .body("addressId",equalTo((int)o.addressId())).body("shippingMethod",equalTo("STANDARD"))
            .body("transactionId",equalTo(o.transactionId())).body("discountAmount",equalTo(2000.0f))
            .body("shippingCost",equalTo(10000.0f)).body("sellerNameSnapshot",equalTo("Tienda principal"))
            .body("delivery.street",equalTo("Calle CU11 123")).body("items.size()",equalTo(1))
            .body("items[0].quantity",equalTo(2)).body("items[0].unitPrice",equalTo(12000.0f))
            .body("items[0].subtotal",equalTo(24000.0f)).body("items[0].productId",equalTo((int)o.productId()))
            .body("history[0].toStatus",equalTo("CONFIRMED")).body("provenance.paymentSource",equalTo("UNKNOWN"));
        assertFalse(r.asString().contains("unitCost"));assertFalse(r.asString().contains("correlationId"));
    }
    @Test void foreignAndMissingDetailsReceiptsAndCancellationReturn404WithoutEffects() throws Exception {
        var own=create();var other=create();var b=buyer(own);
        for(long id:List.of(other.orderId(),Long.MAX_VALUE)) {
            b.get("/api/orders/"+id).then().statusCode(404);
            b.get("/api/orders/"+id+"/receipt").then().statusCode(404);
            b.post("/api/orders/"+id+"/cancellation",REASON).then().statusCode(404);
        }
        unchanged(other,"CONFIRMED");
    }
    @Test void anonymousCannotReadCancelOrDownloadOrders() throws Exception {
        var o=create();var visitor=MarketplaceClient.visitor();
        for(String path:List.of("/api/orders",path(o),path(o)+"/receipt")) visitor.get(path).then().statusCode(401);
        visitor.post(cancel(o),REASON).then().statusCode(401);unchanged(o,"CONFIRMED");
    }
    @Test void sellerActiveRoleHasNoBuyerPermissions() throws Exception {
        var o=create();var seller=MarketplaceClient.loginAs(o.email(),PASSWORD,"VENDEDOR");
        for(String path:List.of("/api/orders",path(o),path(o)+"/receipt")) seller.get(path).then().statusCode(403);
        seller.post(cancel(o),REASON).then().statusCode(403);unchanged(o,"CONFIRMED");
    }
    @Test void inclusiveDateAndStatusFiltersKeepOwnershipAndAllowEmptyResults() throws Exception {
        var a=create("--created-at","2026-01-01T00:00:00");
        var b=create("--email",a.email(),"--created-at","2026-01-01T23:59:59.999999");
        var c=create("--email",a.email(),"--status","IN_PREPARATION","--created-at","2026-01-02T12:00:00");
        create("--created-at","2026-01-01T12:00:00");var client=buyer(a);
        client.get("/api/orders?from=2026-01-01&to=2026-01-01").then().statusCode(200).body("id",equalTo(List.of((int)b.orderId(),(int)a.orderId())));
        client.get("/api/orders?status=IN_PREPARATION&from=2026-01-02&to=2026-01-02").then().statusCode(200).body("id",equalTo(List.of((int)c.orderId())));
        client.get("/api/orders?from=2027-01-01").then().statusCode(200).body("size()",equalTo(0));
    }
    @ParameterizedTest @CsvSource({"from=invalid,INVALID_ORDER_DATE","from=2026-02-30,INVALID_ORDER_DATE","to=0999-01-01,INVALID_ORDER_DATE","from=2026-01-02&to=2026-01-01,INVALID_DATE_RANGE","status=unknown,INVALID_ORDER_STATUS"})
    void invalidFiltersReturnSpecific400(String query,String code) throws Exception {
        var o=create();buyer(o).get("/api/orders?"+query).then().statusCode(400).body("code",equalTo(code));unchanged(o,"CONFIRMED");
    }
    @ParameterizedTest @ValueSource(strings={"CONFIRMED","IN_PREPARATION"})
    void allowedCancellationPersistsReasonHistoryStockAndOneSimulatedRefund(String state) throws Exception {
        var o=create("--status",state);var client=buyer(o);
        client.post(cancel(o),REASON).then().statusCode(200).body("orderId",equalTo((int)o.orderId()))
            .body("status",equalTo("CANCELLED")).body("paymentStatus",equalTo("REFUNDED")).body("refund.status",equalTo("COMPLETED"));
        assertEquals("CANCELLED\tREFUNDED",sql("SELECT status,payment_status FROM orders WHERE id="+o.orderId()));
        assertEquals("BUYER\tOTHER\tFixture de cancelación CU11\t"+o.accountId(),sql("SELECT initiator,reason_code,details,cancelled_by_id FROM order_cancellations WHERE order_id="+o.orderId()));
        assertEquals("10",sql("SELECT stock FROM products WHERE id="+o.productId()));
        assertEquals("COMPLETED\t32000.00\t1\tSIM-REFUND-order-cancel-"+o.orderId(),sql("SELECT status,amount,attempts,provider_reference FROM refunds WHERE order_id="+o.orderId()));
        assertEquals("1",sql("SELECT COUNT(*) FROM order_status_history WHERE order_id="+o.orderId()+" AND from_status='"+state+"' AND to_status='CANCELLED'"));
        client.get(path(o)).then().statusCode(200).body("status",equalTo("CANCELLED"))
            .body("refunds[0].status",equalTo("SIMULATED")).body("refunds[0].confirmedAt",nullValue());
    }
    @ParameterizedTest @ValueSource(strings={"READY_FOR_DISPATCH","PICKED_UP","IN_TRANSIT","DELIVERED","DELIVERY_EXCEPTION","DELIVERY_ATTEMPT_FAILED","RETURNED_TO_SELLER","CANCELLED","CANCELLATION_REQUESTED"})
    void otherStatesRejectCancellationWithoutMutation(String state) throws Exception {
        var o=create("--status",state);buyer(o).post(cancel(o),REASON).then().statusCode(409).body("code",equalTo("ORDER_INVALID_TRANSITION"));unchanged(o,state);
    }
    @Test void aShipmentPreventsCancellationEvenBeforeDispatch() throws Exception {
        var o=create("--shipment");buyer(o).post(cancel(o),REASON).then().statusCode(409).body("code",equalTo("ORDER_HAS_SHIPMENT"));unchanged(o,"CONFIRMED");
    }
    static Stream<String> invalidReasons() {
        return Stream.of("{}","{\"reasonCode\":null}","{\"reasonCode\":\"UNKNOWN\"}","{\"reasonCode\":\"OUT_OF_STOCK\"}",
            "{\"reasonCode\":\"OTHER\"}","{\"reasonCode\":\"OTHER\",\"details\":\"   \"}",
            "{\"reasonCode\":\"OTHER\",\"details\":\""+"x".repeat(1001)+"\"}",
            "{\"reasonCode\":\"CHANGED_MIND\",\"accountId\":123}");
    }
    @ParameterizedTest @MethodSource("invalidReasons") void invalidReasonsAndUnknownIdentityFieldsAreRejected(String body) throws Exception {
        var o=create();buyer(o).post(cancel(o),body).then().statusCode(400);unchanged(o,"CONFIRMED");
    }
    @Test void repeatedCancellationReturnsConflictWithoutSecondRefundOrStockRestoration() throws Exception {
        var o=create();var client=buyer(o);
        client.post(cancel(o),"{\"reasonCode\":\"CHANGED_MIND\"}").then().statusCode(200);
        client.post(cancel(o),REASON).then().statusCode(409).body("code",equalTo("ORDER_INVALID_TRANSITION"));
        assertEquals("1",sql("SELECT COUNT(*) FROM refunds WHERE order_id="+o.orderId()));
        assertEquals("1",sql("SELECT COUNT(*) FROM order_cancellations WHERE order_id="+o.orderId()));
        assertEquals("10",sql("SELECT stock FROM products WHERE id="+o.productId()));
    }
    @Test void concurrentCancellationsProduceOneBusinessEffect() throws Exception {
        var o=create();var a=buyer(o);var b=buyer(o);var start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(()->{start.await();return a.post(cancel(o),REASON);});
            var y=pool.submit(()->{start.await();return b.post(cancel(o),REASON);});
            var statuses=new ArrayList<>(List.of(x.get(30,TimeUnit.SECONDS).statusCode(),y.get(30,TimeUnit.SECONDS).statusCode()));Collections.sort(statuses);
            assertEquals(List.of(200,409),statuses);
        }
        assertEquals("1",sql("SELECT COUNT(*) FROM refunds WHERE order_id="+o.orderId()));
        assertEquals("1",sql("SELECT COUNT(*) FROM order_cancellations WHERE order_id="+o.orderId()));
        assertEquals("10",sql("SELECT stock FROM products WHERE id="+o.productId()));
    }
    @Test void receiptSupportsPdfHeadAndDownloadWithoutCachingPersonalData() throws Exception {
        var o=create();var c=buyer(o);var pdf=c.get(path(o)+"/receipt");
        pdf.then().statusCode(200).contentType("application/pdf");assertTrue(pdf.asByteArray().length>1000);
        assertEquals("%PDF-",new String(Arrays.copyOf(pdf.asByteArray(),5),java.nio.charset.StandardCharsets.US_ASCII));
        assertTrue(pdf.header("Cache-Control").contains("no-store"));assertTrue(pdf.header("Content-Disposition").startsWith("inline"));
        c.get(path(o)+"/receipt?download=true").then().statusCode(200).header("Content-Disposition",containsString("attachment"));
        var head=c.withoutCsrf("HEAD",path(o)+"/receipt","");head.then().statusCode(200).contentType("application/pdf");assertEquals(0,head.asByteArray().length);
    }
    @Test void missingCsrfDoesNotCancelAndLegacyPaymentRemainsGone() throws Exception {
        var o=create();var c=buyer(o);c.withoutCsrf("POST",cancel(o),REASON).then().statusCode(403);unchanged(o,"CONFIRMED");
        c.post("/api/payments/process","{}").then().statusCode(410);
        assertEquals("0",sql("SELECT COUNT(*) FROM payment_checkouts WHERE account_id="+o.accountId()));
    }
    @Test void mercadoPagoFixtureNeverReceivesAFalselyCompletedSimulatedRefund() throws Exception {
        var o=create("--mp");var c=buyer(o);c.post(cancel(o),REASON).then().statusCode(200)
            .body("status",equalTo("CANCELLED")).body("paymentStatus",equalTo("REFUND_PENDING")).body("refund.status",equalTo("FAILED"));
        assertEquals("FAILED\tNULL",sql("SELECT status,provider_reference FROM refunds WHERE order_id="+o.orderId()));
        c.get(path(o)).then().statusCode(200).body("paymentStatus",equalTo("REFUND_PENDING"))
            .body("refunds[0].status",equalTo("FAILED")).body("refunds[0].confirmedAt",nullValue());
    }
}
