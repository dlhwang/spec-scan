package org.membership;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/members")
class MemberController {
    private final MemberService members;
    MemberController(MemberService members) { this.members = members; }
    @GetMapping("/{alias}") Object profile(String alias) { return members.requireMember(alias); }
    @GetMapping Object directory() { return members.directory(); }
    @PostMapping Object join(String email) { return members.join(email); }
    @PutMapping("/{alias}") Object changeEmail(String alias, String email) { return members.changeEmail(alias, email); }
    @DeleteMapping("/{alias}") void leave(String alias) { members.leave(alias); }
    @GetMapping("/active") Object active(boolean enabled) { return members.active(enabled); }
}
