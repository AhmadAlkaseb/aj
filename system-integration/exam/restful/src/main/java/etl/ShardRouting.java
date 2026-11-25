package etl;

import java.util.UUID;

public final class ShardRouting {
    // Hjælpeklasse med statiske metoder; den skal ikke oprettes med new.
    private ShardRouting() { }

    // Samme forfatter giver altid samme shard: 0, 1 eller 2.
    public static int forAuthor(UUID authorId) {
        // hashCode laver UUID'et om til et heltal. floorMod giver resten ved division med 3.
        // Resultatet er altid 0, 1 eller 2, også når hash-værdien er negativ.
        return Math.floorMod(authorId.hashCode(), 3);
    }

    // Bog-ID: forfatterens UUID + '_' + bogens UUID.
    public static UUID authorFromBook(String bookId) {
        // Et UUID fylder 36 tegn. To UUID'er og en underscore giver 36 + 1 + 36 = 73 tegn.
        if (bookId == null || bookId.length() != 73 || bookId.charAt(36) != '_') {
            throw new IllegalArgumentException("Ugyldigt bog-ID");
        }
        // Første del er forfatterens UUID, som bestemmer shard.
        UUID authorId = UUID.fromString(bookId.substring(0, 36));
        // Kontrollerer også, at bogens egen del er et gyldigt UUID.
        UUID.fromString(bookId.substring(37));
        return authorId;
    }

    public static int forBook(String bookId) {
        // Bogen routes ud fra sin forfatter, så deres relation ligger i samme database.
        return forAuthor(authorFromBook(bookId));
    }
}
