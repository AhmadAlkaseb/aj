package com.aj.common;

import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit, audited single-event replay. Confirm publication before removing original. */
public final class ReplayDeadLetter {
    private ReplayDeadLetter() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=2 || args[1].isBlank() || args[1].length()>200) throw new IllegalArgumentException("Supply event UUID and a short operational reason");
        String wanted=Api.guid(args[0]).toString();
        ConnectionFactory factory=new ConnectionFactory();factory.setUri(Api.env("RABBITMQ_URL","amqp://aj:aj-local@localhost:5672/%2f"));factory.setConnectionTimeout(3000);
        try(var connection=factory.newConnection();var channel=connection.createChannel()) {
            Bus.topology(channel);channel.confirmSelect();List<Long> pending=new ArrayList<>();boolean replayed=false;
            for(int i=0;i<1000;i++) {
                var message=channel.basicGet("aj.dead",false);if(message==null)break;
                long tag=message.getEnvelope().getDeliveryTag();
                if(!wanted.equals(message.getProps().getMessageId())) {pending.add(tag);continue;}
                String topic=message.getEnvelope().getRoutingKey();
                Events.decode(wanted,topic,Json.read(new String(message.getBody(),StandardCharsets.UTF_8)));
                channel.basicPublish("aj.events",topic,message.getProps(),message.getBody());channel.waitForConfirmsOrDie(5000);
                channel.basicAck(tag,false);replayed=true;
                Telemetry.log(Map.of("event","operator.replay","eventId",wanted,"topic",topic,"reason",args[1]));break;
            }
            for(long tag:pending)channel.basicNack(tag,false,true);
            if(!replayed)throw new IllegalArgumentException("Event not found in first 1000 dead letters");
        }
    }
}
