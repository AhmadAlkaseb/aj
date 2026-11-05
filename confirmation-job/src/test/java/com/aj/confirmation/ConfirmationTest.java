package com.aj.confirmation;

import com.aj.common.Json;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmationTest {
    @Test void createsReadableInvoiceWithoutForwardingUnrelatedCustomerInput() throws Exception {
        UUID order=UUID.randomUUID(),car=UUID.randomUUID();
        var data=Json.read(Json.write(Map.of("orderGuid",order,"productGuid",car,"totalAmount",123456.78,
            "currency","DKK","email","private@example.test")));
        try(var pdf=Loader.loadPDF(Application.document(data))) {
            assertEquals(1,pdf.getNumberOfPages());
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains(order.toString()));assertTrue(text.contains("123456.78 DKK"));
            assertFalse(text.contains("private@example.test"));
        }
    }
}
