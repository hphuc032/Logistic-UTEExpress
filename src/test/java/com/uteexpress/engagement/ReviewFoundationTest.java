package com.uteexpress.engagement;

import com.uteexpress.engagement.dto.*;
import com.uteexpress.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ReviewFoundationTest {
    @Test void stripsBeforeCheckingBoundsAndKeepsPlainText() {
        String text = "<script>plain text, never render as HTML</script>" + "x".repeat(10);
        assertThat(new ReviewContent(5,"  " + text + "  ").comment()).isEqualTo(text);
        assertThat(new ReviewContent(1,"x".repeat(50)).rating()).isEqualTo(1);
        assertThat(new ReviewContent(5,"x".repeat(2000)).comment()).hasSize(2000);
    }
    @ParameterizedTest @ValueSource(ints={0,6,-1,100})
    void rejectsInvalidRating(int value) {
        assertThatThrownBy(() -> new ReviewContent(value,"x".repeat(50))).isInstanceOf(ApplicationException.class);
    }
    @Test void rejectsBlankNullShortAndOversizedContent() {
        for(String s:new String[]{null," ".repeat(60),"x".repeat(49),"x".repeat(2001)})
            assertThatThrownBy(() -> new ReviewContent(3,s)).isInstanceOf(ApplicationException.class);
    }
    @Test void countAndImagePolicyMatchStorageLimits() {
        for(int i=0;i<=3;i++) ReviewImagePolicy.requireCount(i);
        assertThatThrownBy(() -> ReviewImagePolicy.requireCount(4)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> ReviewImagePolicy.requireCount(-1)).isInstanceOf(ApplicationException.class);
        assertThat(ReviewImagePolicy.STORAGE.maxBytes()).isEqualTo(5L*1024*1024);
    }
    @ParameterizedTest @ValueSource(ints={49,50,1999,2000,2001})
    void unicodeCodePointBoundariesMatchPostgres(int count) {
        String emoji="\uD83D\uDE00".repeat(count);
        if(count>=50 && count<=2000) assertThat(new ReviewContent(5,"  "+emoji+"  ").comment()).isEqualTo(emoji);
        else assertThatThrownBy(() -> new ReviewContent(5,emoji)).isInstanceOf(ApplicationException.class);
    }
    @Test void combiningMarksAreCodePointsAndMalformedSurrogatesAreRejected() {
        assertThat(new ReviewContent(5,"e\u0301".repeat(25)).comment().codePointCount(0,50)).isEqualTo(50);
        assertThatThrownBy(() -> new ReviewContent(5,"x".repeat(49)+(char)0xD800)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new ReviewContent(5,"x".repeat(49)+(char)0xDC00)).isInstanceOf(ApplicationException.class);
    }
}
