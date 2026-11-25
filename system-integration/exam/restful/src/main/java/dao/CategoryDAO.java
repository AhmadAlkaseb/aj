package dao;

import jakarta.persistence.EntityManager;
import etl.DatabaseResourceManager;
import persistence.model.*;
import java.util.*;

public final class CategoryDAO {
    private static final CategoryDAO INSTANCE = new CategoryDAO();
    private CategoryDAO() { }
    public static CategoryDAO getInstance() { return INSTANCE; }

    // Returnerer null, hvis ID'et ikke findes.
    public Category findById(UUID id) {
        try (EntityManager em = DatabaseResourceManager.factory(0).createEntityManager()) {
            var results = em.createQuery("select e from Category e where e.id = :id", Category.class)
                    .setParameter("id", id).getResultList();
            return results.isEmpty() ? null : results.get(0);
        }
    }

    public List<Category> findAll(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page skal være >= 0 og size mellem 1 og 100");
        }
        int offset = Math.multiplyExact(page, size);
        // Manglende søgetekst bliver tom tekst; søgningen bruger små bogstaver.
        String term = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<Category> all = new ArrayList<>();
        for (int shard = 0; shard < 1; shard++) {
            try (EntityManager em = DatabaseResourceManager.factory(shard).createEntityManager()) {
                // locate finder søgetekstens position. Over 0 betyder, at teksten findes.
                // %, _ og ! er almindelige tegn; setParameter holder input adskilt fra forespørgslen.
                all.addAll(em.createQuery("select e from Category e where locate(:search, lower(e.name)) > 0", Category.class)
                        .setParameter("search", term).getResultList());
            }
        }
        // Sortér samlet, før siden vælges. Ellers ville hver shard give sin egen side.
        all.sort(Comparator.comparing(e -> e.getId().toString()));
        return all.stream().skip(offset).limit(size).toList();
    }

    public Category create(Category entity) {
        // Kategorier er fælles referencedata med samme UUID på alle tre shards.
        for (int shard = 0; shard < 3; shard++) {
            try (EntityManager em = DatabaseResourceManager.factory(shard).createEntityManager()) {
                var tx = em.getTransaction();
                try {
                    tx.begin();
                    em.merge(entity);
                    tx.commit();
                } catch (RuntimeException exception) {
                    if (tx.isActive()) tx.rollback();
                    throw exception;
                }
            }
        }
        return entity;
    }

    public Category update(UUID id, Category replacement) {
        Category existing = findById(id);
        if (existing == null) return null;
        existing.setName(replacement.getName());
        return create(existing);
    }

    public boolean delete(UUID id) {
        boolean found = false;
        for (int shard = 0; shard < 3; shard++) {
            try (EntityManager em = DatabaseResourceManager.factory(shard).createEntityManager()) {
                var tx = em.getTransaction();
                try {
                    tx.begin();
                    Category category = em.find(Category.class, id);
                    if (category != null) {
                        em.remove(category);
                        found = true;
                    }
                    tx.commit();
                } catch (RuntimeException exception) {
                    if (tx.isActive()) tx.rollback();
                    throw exception;
                }
            }
        }
        return found;
    }
}
