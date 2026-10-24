package com.uteexpress.engagement.controller;

import com.uteexpress.common.exception.*;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.engagement.dto.CreateReviewRequest;
import com.uteexpress.engagement.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ReviewController {
    private final ReviewService reviews;
    private final PublicReviewService publicReviews;
    public ReviewController(ReviewService reviews,PublicReviewService publicReviews) {this.reviews=reviews;this.publicReviews=publicReviews;}
    @GetMapping("/order-items/{id}/review")
    @PreAuthorize("hasAnyAuthority('ROLE_USER','ROLE_VENDOR')")
    public String form(@PathVariable Long id,Model model) {reviews.requireForm(id);model.addAttribute("itemId",id);return "engagement/review-form";}
    @PostMapping(value="/order-items/{id}/review",consumes=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_USER','ROLE_VENDOR')") @ResponseBody
    public Map<String,Long> json(@PathVariable Long id,@Valid @RequestBody CreateReviewRequest body,HttpServletRequest request) {
        fields(request,Set.of("_csrf"));return Map.of("reviewId",reviews.create(id,body.rating(),body.comment(),List.of()));
    }
    @PostMapping(value="/order-items/{id}/review",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_USER','ROLE_VENDOR')")
    public String submit(@PathVariable Long id,@RequestParam Integer rating,@RequestParam String comment,
            @RequestParam(required=false) List<MultipartFile> images,HttpServletRequest request) throws IOException,jakarta.servlet.ServletException {
        fields(request,Set.of("rating","comment","_csrf"));
        for(var part:request.getParts()) if(!Set.of("rating","comment","images","_csrf").contains(part.getName())) throw invalid();
        var nonempty=images==null ? List.<MultipartFile>of() : images.stream().filter(file -> !file.isEmpty()).toList();
        if(nonempty.size()>3) throw invalid();
        var uploads=new ArrayList<UploadContent>();
        for(var file:nonempty) {
            if(file.getSize()>5L*1024*1024) throw invalid();
            uploads.add(new UploadContent(file.getBytes(),file.getOriginalFilename(),file.getContentType()));
        }
        reviews.create(id,rating,comment,uploads);
        return "redirect:/orders";
    }
    @GetMapping("/products/{id}/reviews")
    public String list(@PathVariable Long id,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("reviews",publicReviews.list(id,page));model.addAttribute("productId",id);model.addAttribute("page",page);return "engagement/reviews";
    }
    @GetMapping("/products/{productId}/reviews/media/{mediaId}") @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable Long productId,@PathVariable Long mediaId) {
        var file=publicReviews.image(productId,mediaId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.parseMediaType(file.mediaType())).body(file.bytes());
    }
    private static void fields(HttpServletRequest request,Set<String> allowed) {
        if(!allowed.containsAll(request.getParameterMap().keySet()) || request.getParameterMap().values().stream().anyMatch(values -> values.length!=1)) throw invalid();
    }
    private static ApplicationException invalid() {return new ApplicationException(ErrorCode.INVALID_REQUEST);}
}
