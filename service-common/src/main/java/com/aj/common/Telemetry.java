package com.aj.common;

import io.javalin.Javalin;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;

/** Bounded labels, structured operational logs and Prometheus exposition; no payloads/tokens. */
public final class Telemetry {
    private static final double[] BOUNDS={.01,.05,.1,.25,.5,1,2,5,10,60,Double.POSITIVE_INFINITY};
    private static final Map<String,Sample> HTTP=new ConcurrentHashMap<>();
    private static final Map<String,Double> GAUGES=new ConcurrentHashMap<>();
    private static class Sample { final LongAdder count=new LongAdder();final DoubleAdder sum=new DoubleAdder();final LongAdder[] buckets=Arrays.stream(BOUNDS).mapToObj(x->new LongAdder()).toArray(LongAdder[]::new); }
    private Telemetry() {}
    public static void gauge(String name,double value){GAUGES.put(name,value);}
    public static void correlation(io.javalin.http.Context ctx,Object id){ctx.attribute("correlationId",id.toString());ctx.header("X-Correlation-ID",id.toString());}
    public static void log(Map<String,Object> fields){var row=new LinkedHashMap<String,Object>();row.put("timestamp",Instant.now().toString());row.putAll(fields);System.out.println(Json.write(row));}
    public static void install(Javalin app,String service) {
        app.before(ctx -> {
            ctx.attribute("startedNanos",System.nanoTime());
            String correlation=ctx.header("X-Correlation-ID");
            try {correlation=Api.guid(correlation).toString();}catch(Exception ex){correlation=UUID.randomUUID().toString();}
            ctx.attribute("correlationId",correlation);ctx.header("X-Correlation-ID",correlation);
        });
        app.after(ctx -> {
            Long start=ctx.attribute("startedNanos");if(start==null || ctx.path().equals("/metrics"))return;
            double seconds=(System.nanoTime()-start)/1e9;
            String route; try {route=Objects.toString(ctx.endpointHandlerPath(),"unmatched").replace("\"","");} catch(Exception ex){route="unmatched";}
            String key="service=\""+service+"\",method=\""+ctx.method()+"\",route=\""+route+"\",status=\""+ctx.statusCode()+"\"";
            Sample sample=HTTP.computeIfAbsent(key,x->new Sample());sample.count.increment();sample.sum.add(seconds);
            for(int i=0;i<BOUNDS.length;i++)if(seconds<=BOUNDS[i])sample.buckets[i].increment();
            log(Map.of("service",service,"event","http.request","route",route,"status",ctx.statusCode(),"durationMs",Math.round(seconds*1000),"correlationId",Objects.toString(ctx.attribute("correlationId"),"")));
        });
        app.get("/metrics",ctx -> {
            StringBuilder out=new StringBuilder();
            HTTP.forEach((key,s)->{
                out.append("aj_http_requests_total{").append(key).append("} ").append(s.count.sum()).append('\n');
                out.append("aj_http_duration_seconds_sum{").append(key).append("} ").append(s.sum.sum()).append('\n');
                out.append("aj_http_duration_seconds_count{").append(key).append("} ").append(s.count.sum()).append('\n');
                for(int i=0;i<BOUNDS.length;i++)out.append("aj_http_duration_seconds_bucket{").append(key).append(",le=\"").append(Double.isInfinite(BOUNDS[i])?"+Inf":BOUNDS[i]).append("\"} ").append(s.buckets[i].sum()).append('\n');
            });
            GAUGES.forEach((key,value)->out.append("aj_").append(key).append("{service=\"").append(service).append("\"} ").append(value).append('\n'));
            ctx.contentType("text/plain; version=0.0.4; charset=utf-8").result(out.toString());
        });
    }
}
