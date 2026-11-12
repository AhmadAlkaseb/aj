package com.aj.confirmation;

import com.aj.common.*;
import com.rabbitmq.client.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** ScaledJob handles one event; the local Compose worker can remain available continuously. */
public final class Application {
    private Application() {}
    public static byte[] document(JsonNode data) throws java.io.IOException {
        String order=Api.guid(data,"orderGuid").toString();
        var lines=java.util.List.of("AJ MOTORS - ORDER CONFIRMATION","Order: "+order,
            "Car: "+Api.guid(data,"productGuid"),"Total: "+Api.money(data,"totalAmount")+" "+Api.currency(data),
            "Payment registered. No bank transfer is performed by this document.");
        try(var pdf=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            var page=new PDPage();pdf.addPage(page);
            try(var content=new PDPageContentStream(pdf,page)) {
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),11);content.setLeading(24);content.newLineAtOffset(45,740);
                for(String line:lines){content.showText(line);content.newLine();} content.endText();
            }
            pdf.save(bytes);return bytes.toByteArray();
        }
    }
    public static void main(String[] args) throws Exception {
        boolean continuous=Boolean.parseBoolean(Api.env("CONFIRMATION_WORKER","false"));
        do {
            try { process(continuous); }
            catch(Exception failure) {
                if(!continuous)throw failure;
                System.err.println("Confirmation worker will retry: "+failure.getClass().getSimpleName());
                Thread.sleep(3000);
            }
        } while(continuous);
    }
    private static void process(boolean continuous) throws Exception {
        ConnectionFactory factory=new ConnectionFactory();factory.setUri(Api.env("RABBITMQ_URL","amqp://aj:aj-local@localhost:5672/%2f"));
        factory.setConnectionTimeout(3000);factory.setAutomaticRecoveryEnabled(false);
        try(var connection=factory.newConnection();var channel=connection.createChannel()) {
            Bus.topology(channel);
            do {
            var message=channel.basicGet("aj.confirmations",false);
            if(message==null){if(!continuous)return;Thread.sleep(1000);continue;}
            try {
                var event=Events.decode(message.getProps().getMessageId(),message.getEnvelope().getRoutingKey(),Json.read(new String(message.getBody(),StandardCharsets.UTF_8)));
                String order=Api.guid(event.payload(),"orderGuid").toString();byte[] pdf=document(event.payload());
                var request=HttpRequest.newBuilder(URI.create(Api.env("ORDERS_URL","http://localhost:7004")+"/internal/orders/"+order+"/confirmation"))
                    .timeout(Duration.ofSeconds(8)).header("Content-Type","application/pdf").header("X-Confirmation-Token",Api.env("CONFIRMATION_TOKEN","aj-confirmation-local"))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(pdf)).build();
                var response=Api.HTTP.send(request,HttpResponse.BodyHandlers.discarding());
                if(response.statusCode()!=204)throw new IllegalStateException("Confirmation owner returned "+response.statusCode());
                channel.basicAck(message.getEnvelope().getDeliveryTag(),false);
                System.out.println(Json.write(java.util.Map.of("event","confirmation.stored","correlationId",event.correlationId(),"orderGuid",order)));
            } catch(IllegalArgumentException | io.javalin.http.BadRequestResponse invalid) {
                channel.basicReject(message.getEnvelope().getDeliveryTag(),false);
                System.err.println("Malformed confirmation event dead-lettered");
            } catch(Exception failure) {
                channel.basicNack(message.getEnvelope().getDeliveryTag(),false,true);throw failure;
            }
            } while(continuous);
        }
    }
}
