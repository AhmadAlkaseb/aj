package dao;

import jakarta.persistence.EntityManager;
import etl.DatabaseResourceManager;
import etl.ShardRouting;
import persistence.model.*;
import java.util.*;

public final class BookDAO {
    private static final BookDAO INSTANCE = new BookDAO();
    private BookDAO() { }
    public static BookDAO getInstance() { return INSTANCE; }

    // Returnerer null, hvis ID'et ikke findes.
    public Book findById(String id) {
        try (EntityManager em = DatabaseResourceManager.factory(ShardRouting.forBook(id)).createEntityManager()) {
            var results = em.createQuery("select e from Book e join fetch e.author join fetch e.category where e.id = :id", Book.class)
                    .setParameter("id", id).getResultList();
            return results.isEmpty() ? null : results.get(0);
        }
    }

    public List<Book> findAll(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page skal være >= 0 og size mellem 1 og 100");
        }
        int offset = Math.multiplyExact(page, size);
        // Manglende søgetekst bliver tom tekst; søgningen bruger små bogstaver.
        String term = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<Book> all = new ArrayList<>();
        for (int shard = 0; shard < 3; shard++) {
            try (EntityManager em = DatabaseResourceManager.factory(shard).createEntityManager()) {
                // locate finder søgetekstens position. Over 0 betyder, at teksten findes.
                // %, _ og ! er almindelige tegn; setParameter holder input adskilt fra forespørgslen.
                all.addAll(em.createQuery("select e from Book e join fetch e.author join fetch e.category where locate(:search, lower(e.title)) > 0", Book.class)
                        .setParameter("search", term).getResultList());
            }
        }
        // Sortér samlet, før siden vælges. Ellers ville hver shard give sin egen side.
        all.sort(Comparator.comparing(e -> e.getId().toString()));
        return all.stream().skip(offset).limit(size).toList();
    }

    public Book create(Book entity) {
        int shard = ShardRouting.forBook(entity.getId());
        try (EntityManager em = DatabaseResourceManager.factory(shard).createEntityManager()) {
            var tx = em.getTransaction();
            try {
                tx.begin();
                if (!ShardRouting.authorFromBook(entity.getId()).equals(entity.getAuthor().getId())) {
                    throw new IllegalArgumentException("Bog-ID og forfatter passer ikke sammen");
                }
                entity.setAuthor(em.find(Author.class, entity.getAuthor().getId()));
                entity.setCategory(em.find(Category.class, entity.getCategory().getId()));
                em.persist(entity);
                tx.commit();
                return entity;
            } catch (RuntimeException exception) {
                if (tx.isActive()) tx.rollback();
                throw exception;
            }
        }
    }

    public Book update(String id, Book replacement) {
        try (EntityManager em = DatabaseResourceManager.factory(ShardRouting.forBook(id)).createEntityManager()) {
            var tx = em.getTransaction();
            try {
                tx.begin();
                Book existing = em.find(Book.class, id);
                if (existing == null) {
                    tx.rollback();
                    return null;
                }
                if (!existing.getAuthor().getId().equals(replacement.getAuthor().getId())) {
                    throw new IllegalArgumentException("En bogs forfatter kan ikke ændres; opret i stedet en ny bog");
                }
                existing.setCategory(em.find(Category.class, replacement.getCategory().getId()));
                existing.setTitle(replacement.getTitle());
                tx.commit();
                return existing;
            } catch (RuntimeException exception) {
                if (tx.isActive()) tx.rollback();
                throw exception;
            }
        }
    }

    public boolean delete(String id) {
        try (EntityManager em = DatabaseResourceManager.factory(ShardRouting.forBook(id)).createEntityManager()) {
            var tx = em.getTransaction();
            try {
                tx.begin();
                Book existing = em.find(Book.class, id);
                if (existing == null) {
                    tx.rollback();
                    return false;
                }
                em.remove(existing);
                tx.commit();
                return true;
            } catch (RuntimeException exception) {
                if (tx.isActive()) tx.rollback();
                throw exception;
            }
        }
    }
}
