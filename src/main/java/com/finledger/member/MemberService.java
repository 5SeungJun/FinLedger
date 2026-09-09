package com.finledger.member;

import com.finledger.common.error.BusinessException;
import com.finledger.common.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member create(String name) {
        return memberRepository.save(new Member(name));
    }

    public Member get(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
