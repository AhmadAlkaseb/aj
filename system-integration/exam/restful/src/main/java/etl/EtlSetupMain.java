package etl;

import persistence.DatabaseSeeder;
import java.util.Arrays;

// Valgfri opsætning fra terminalen. Main udfører selv seed ved hver opstart.
public class EtlSetupMain {
    public static void main(String[] args) throws Exception {
        try {
            // Nulstiller den almindelige database til 50 forfattere, kategorier og bøger.
            DatabaseSeeder.seed();
            // Argumentet --etl aktiverer også fordeling til shards og indlæsning i warehouse.
            if (Arrays.asList(args).contains("--etl")) {
                System.out.println(new WarehouseEtlService().run());
            } else {
                System.out.println("Normal database: 50 forfattere, 50 kategorier og 50 bøger. Shards afventer ETL.");
            }
        } finally {
            // Kører både ved succes og fejl, så oprettede Hibernate-factories bliver lukket.
            DatabaseResourceManager.close();
        }
    }
}
