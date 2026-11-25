package dao;

import jakarta.persistence.EntityManager;
import etl.DatabaseResourceManager;
import etl.ShardRouting;
import persistence.model.*;
import java.util.*;

public final class AuthorDAO {
    private static final AuthorDAO INSTANCE = new AuthorDAO();
    private AuthorDAO() { }
    public static AuthorDAO getInstance() { return INSTANCE; }

    // Returnerer null, hvis ID'et ikke findes.
    public Author findById(UUID id) {
        try (EntityManager em = DatabaseResourceManager.factory(ShardRouting.forAuthor(id)).createEntityManager()) {
            var results = em.createQuery("select e from Author e where e.id = :id", Author.class)
                    .setParameter("id", id).getResultList();
            return results.isEmpty() ? null : results.get(0);
        }
    }

    public List<Author> findAll(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page skal være >= 0 og size mellem 1 og 100");
        }
        int offset = Math.multiplyExact(page, size);
        // Manglende søgetekst bliver tom tekst; søgningen bruger små bogstaver.
        String term = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<Author> all = new ArrayList<>();
        for (int shard = 0; shard < 3; shard++) {
            try (EntityManager em = DatabaseResourceManager.factory(shard).createEntityManager()) {
                // locate finder søgetekstens position. Over 0 betyder, at teksten findes.
                // %, _ og ! er almindelige tegn; setParameter holder input adskilt fra forespørgslen.
                all.addAll(em.createQuery("select e from Author e where locate(:search, lower(e.name)) > 0", Author.class)
                        .setParameter("search", term).getResultList());
            }
        }
        // Sortér samlet, før siden vælges. Ellers ville hver shard give sin egen side.
        all.sort(Comparator.comparing(e -> e.getId().toString()));
        return all.stream().skip(offset).limit(size).toList();
    }

    public Author create(Author entity) {
        int shard = ShardRouting.forAuthor(entity.getId());
        try (EntityManager em = DatabaseResourceManager.factory(shard).createEntityManager()) {
            var tx = em.getTransaction();
            try {
                tx.begin();
                em.persist(entity);
                tx.commit();
                return entity;
            } catch (RuntimeException exception) {
                if (tx.isActive()) tx.rollback();
                throw exception;
            }
        }
    }

    public Author update(UUID id, Author replacement) {
        try (EntityManager em = DatabaseResourceManager.factory(ShardRouting.forAuthor(id)).createEntityManager()) {
            var tx = em.getTransaction();
            try {
                tx.begin();
                Author existing = em.find(Author.class, id);
                if (existing == null) {
                    tx.rollback();
                    return null;
                }
                existing.setName(replacement.getName());
                tx.commit();
                return existing;
            } catch (RuntimeException exception) {
                if (tx.isActive()) tx.rollback();
                throw exception;
            }
        }
    }

    public boolean delete(UUID id) {
        try (EntityManager em = DatabaseResourceManager.factory(ShardRouting.forAuthor(id)).createEntityManager()) {
            var tx = em.getTransaction();
            try {
                tx.begin();
                Author existing = em.find(Author.class, id);
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
