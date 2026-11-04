package app;

import com.aj.common.Db;
import com.aj.common.demo.DemoData;
import persistence.entities.User;
import persistence.entities.UserDetails;

public final class DemoSeeder {
    private DemoSeeder() {}
    public static void seed(Db db) {
        DemoData.seed(db, "User", "users", (em, index) -> {
            User user = new User();
            user.setGuid(DemoData.guid("users", index));
            UserDetails details = UserDetails.builder()
                .user(user)
                .firstName(DemoData.FIRST_NAMES.get(index - 1))
                .lastName(DemoData.LAST_NAMES.get(index - 1))
                .email(DemoData.email(index))
                .phoneNumber("")
                .address("Demovej " + index)
                .postalCode("2100")
                .city("København Ø")
                .createdAt(DemoData.CREATED_AT.plusDays(index))
                .build();
            user.setDetails(details);
            em.persist(user);
        });
    }
}
