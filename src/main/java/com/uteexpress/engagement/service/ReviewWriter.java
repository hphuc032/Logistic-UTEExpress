package com.uteexpress.engagement.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.common.storage.*;
import com.uteexpress.engagement.dto.*;
import com.uteexpress.order.dto.ReviewableOrderItem;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class ReviewWriter {
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final FileStorageService storage;
    private final Clock clock;
    public ReviewWriter(ObjectProvider<JdbcTemplate> jdbc,FileStorageService storage,Clock clock) {
        this.jdbc=jdbc;this.storage=storage;this.clock=clock;
    }
    public long write(ReviewableOrderItem facts,ReviewContent content,List<UploadContent> uploads) {
        ReviewImagePolicy.requireCount(uploads.size());
        var keys=new ArrayList<String>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status!=STATUS_COMMITTED) for(String key:keys) {
                    try {storage.delete(key);} catch(RuntimeException failure) {
                        org.slf4j.LoggerFactory.getLogger(ReviewWriter.class).warn("Review rollback file cleanup failed; reconciliation required");
                    }
                }
            }
        });
        long id=jdbc.getObject().queryForObject("""
                INSERT INTO uteexpress.reviews(order_item_id,order_id,user_id,product_id,rating,comment,created_at)
                VALUES (?,?,?,?,?,?,?) RETURNING id
                """,Long.class,facts.orderItemId(),facts.orderId(),facts.buyerId(),facts.productId(),
                content.rating(),content.comment(),Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS)));
        for(int i=0;i<uploads.size();i++) {
            StoredFile file=storage.storeImage("reviews",uploads.get(i),ReviewImagePolicy.STORAGE);
            keys.add(file.key());
            jdbc.getObject().update("INSERT INTO uteexpress.review_media(review_id,media_type,storage_key,position) VALUES (?,?,?,?)",id,file.mediaType(),file.key(),i);
        }
        return id;
    }
}
