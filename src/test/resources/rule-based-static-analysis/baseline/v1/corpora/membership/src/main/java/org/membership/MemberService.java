package org.membership;
import java.util.*;
class MemberService {
    private final MemberRepository members;
    MemberService(MemberRepository members) { this.members = members; }
    Object requireMember(String alias) { return members.find(alias).orElseThrow(NoSuchElementException::new); }
    List<Object> directory() { return members.list(); }
    Object join(String email) { if (email == null || !email.contains("@")) throw new IllegalArgumentException(); return members.add(email); }
    Object changeEmail(String alias, String email) { if (email.isBlank()) throw new IllegalArgumentException(); return members.change(alias, email); }
    void leave(String alias) { Objects.requireNonNull(alias); members.delete(alias); }
    List<Object> active(boolean enabled) { return members.byStatus(enabled); }
}
