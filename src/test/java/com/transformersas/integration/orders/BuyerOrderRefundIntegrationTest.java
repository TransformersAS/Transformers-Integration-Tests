package com.transformersas.integration.orders;

import com.transformersas.integration.support.MarketplaceClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.transformersas.integration.orders.Cu11Fixture.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** The runner restarts ONLY the disposable backend with each supported simulator mode. */
@EnabledIfEnvironmentVariable(named="CU11_ISOLATED",matches="true")
class BuyerOrderRefundIntegrationTest {
    @Test void cancellationSurvivesPendingOrUnavailableRefundProvider() throws Exception {
        String mode=System.getenv("CU11_REFUND_MODE");assertTrue(java.util.Set.of("PENDING","UNAVAILABLE").contains(mode));
        var o=create();var buyer=MarketplaceClient.loginAs(o.email(),PASSWORD,"COMPRADOR");
        String refund=mode.equals("PENDING")?"PENDING":"FAILED";
        buyer.post(cancel(o),REASON).then().statusCode(200).body("status",equalTo("CANCELLED"))
            .body("paymentStatus",equalTo("REFUND_PENDING")).body("refund.status",equalTo(refund));
        assertEquals("CANCELLED\tREFUND_PENDING",sql("SELECT status,payment_status FROM orders WHERE id="+o.orderId()));
        assertEquals(refund+"\t1",sql("SELECT status,attempts FROM refunds WHERE order_id="+o.orderId()));
        assertEquals("1",sql("SELECT COUNT(*) FROM order_cancellations WHERE order_id="+o.orderId()));
        assertEquals("10",sql("SELECT stock FROM products WHERE id="+o.productId()));
        buyer.get(path(o)).then().statusCode(200).body("status",equalTo("CANCELLED")).body("refunds[0].status",equalTo(refund));
        buyer.post(cancel(o),REASON).then().statusCode(409);
        assertEquals("1",sql("SELECT COUNT(*) FROM refunds WHERE order_id="+o.orderId()));
    }
}
