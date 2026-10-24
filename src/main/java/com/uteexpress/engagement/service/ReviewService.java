package com.uteexpress.engagement.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.common.storage.*;
import com.uteexpress.engagement.dto.*;
import com.uteexpress.order.service.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.dao.DataIntegrityViolationException;

@Service
@PreAuthorize("hasAnyAuthority('ROLE_USER','ROLE_VENDOR')")
public class ReviewService {
    private final OrderReviewWriteBoundary boundary;
    private final OrderEligibilityQueryService eligibility;
    private final ReviewWriter writer;
    public ReviewService(OrderReviewWriteBoundary boundary,OrderEligibilityQueryService eligibility,ReviewWriter writer) {
        this.boundary=boundary;this.eligibility=eligibility;this.writer=writer;
    }
    public void requireForm(Long item) {
        if(!eligibility.isCurrentBuyerEligibleToReview(item)) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    public long create(Long item,Integer rating,String comment,List<UploadContent> images) {
        if(rating==null || images==null) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        var content=new ReviewContent(rating,comment);
        ReviewImagePolicy.requireCount(images.size());
        var snapshot=List.copyOf(images);
        try {return boundary.withEligibleCurrentBuyerItem(item,facts -> writer.write(facts,content,snapshot));}
        catch(DataIntegrityViolationException duplicate) {throw new ApplicationException(ErrorCode.CONFLICT);}
        catch(StorageException invalid) {
            throw new ApplicationException(invalid.reason()==StorageException.Reason.IO_FAILURE ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED);
        }
    }
}
