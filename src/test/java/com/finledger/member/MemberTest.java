package com.finledger.member;

import com.finledger.common.error.BusinessException;
import com.finledger.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    @Test
    void createsMemberWithTrimmedName() {
        Member member = new Member("  홍길동  ");

        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new Member("  "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MEMBER_NAME);
    }
}
