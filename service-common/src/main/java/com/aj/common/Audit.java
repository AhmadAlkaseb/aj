package com.aj.common;

import jakarta.persistence.EntityManager;
import java.util.*;

public final class Audit {
    private Audit() {}
    public static void status(EntityManager em,UUID aggregate,String before,String after) {
        if(Objects.equals(before,after)) return;
        em.createNativeQuery("insert into state_audit(aggregate_guid,old_status,new_status,correlation_id) values(:id,:before,:after,:correlation)")
            .setParameter("id",aggregate).setParameter("before",before).setParameter("after",after).setParameter("correlation",Events.correlationOr(aggregate.toString())).executeUpdate();
    }
    public static List<Map<String,Object>> history(EntityManager em,UUID aggregate) {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Object raw:em.createNativeQuery("select old_status,new_status,recorded_at from state_audit where aggregate_guid=:id order by id").setParameter("id",aggregate).getResultList()) {
            Object[] r=(Object[])raw; var row=new LinkedHashMap<String,Object>();row.put("from",r[0]);row.put("to",r[1]);row.put("at",r[2].toString());rows.add(row);
        }
        return rows;
    }
}
