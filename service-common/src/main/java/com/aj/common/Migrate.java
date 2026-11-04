package com.aj.common;
/** Deployment migration gate; uses the service image's own migration resources. */
public final class Migrate {
    private Migrate() {}
    public static void main(String[] args) {
        if(args.length!=1 || !java.util.Set.of("users","products","orders","payments").contains(args[0])) throw new IllegalArgumentException("Expected owning service name");
        org.flywaydb.core.Flyway.configure().dataSource(Api.env("DB_URL",""),Api.env("DB_USER","postgres"),Api.env("DB_PASSWORD","postgres"))
            .locations("classpath:db/migration/"+args[0]).cleanDisabled(true).load().migrate();
    }
}
