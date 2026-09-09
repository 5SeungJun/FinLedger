package com.finledger.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse create(@Valid @RequestBody CreateMemberRequest request) {
        return MemberResponse.from(memberService.create(request.name()));
    }

    public record CreateMemberRequest(@NotBlank String name) {
    }

    public record MemberResponse(Long id, String name, Instant createdAt) {

        static MemberResponse from(Member member) {
            return new MemberResponse(member.getId(), member.getName(), member.getCreatedAt());
        }
    }
}
