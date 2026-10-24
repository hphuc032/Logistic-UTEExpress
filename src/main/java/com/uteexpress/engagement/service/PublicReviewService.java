package com.uteexpress.engagement.service;

import com.uteexpress.catalog.service.PublicCatalogService;
import com.uteexpress.common.exception.*;
import com.uteexpress.common.storage.*;
import com.uteexpress.engagement.dto.ReviewView;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PublicReviewService {
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final PublicCatalogService catalog;
    private final FileStorageService storage;
    public PublicReviewService(ObjectProvider<JdbcTemplate> jdbc,PublicCatalogService catalog,FileStorageService storage) {
        this.jdbc=jdbc;this.catalog=catalog;this.storage=storage;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<ReviewView> list(Long product,int page) {
        if(page<0 || page>10000) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        catalog.product(product);
        return jdbc.getObject().query("""
                SELECT id,rating,comment,created_at FROM uteexpress.reviews
                WHERE product_id=? AND visibility='PUBLIC' ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?
                """,(r,n) -> new ReviewView(r.getLong("id"),r.getInt("rating"),r.getString("comment"),r.getTimestamp("created_at").toInstant(),
                    jdbc.getObject().queryForList("SELECT id FROM uteexpress.review_media WHERE review_id=? ORDER BY position",Long.class,r.getLong("id"))),product,(long)page*20);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public StoredContent image(Long product,Long media) {
        catalog.product(product);
        if(media==null || media<=0) throw missing();
        var keys=jdbc.getObject().queryForList("""
                SELECT m.storage_key FROM uteexpress.review_media m JOIN uteexpress.reviews r ON r.id=m.review_id
                WHERE m.id=? AND r.product_id=? AND r.visibility='PUBLIC'
                """,String.class,media,product);
        if(keys.isEmpty()) throw missing();
        try {return storage.read(keys.getFirst());} catch(StorageException absent) {throw missing();}
    }
    private static ApplicationException missing() {return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);}
}
