package org.membership;
import java.util.*;
interface MemberRepository {
    Optional<Object> find(String alias); List<Object> list(); Object add(String email);
    Object change(String alias, String email); void delete(String alias); List<Object> byStatus(boolean enabled);
}
