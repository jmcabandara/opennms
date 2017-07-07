package org.opennms.netmgt.correlation.drools;

import java.util.Objects;
import java.util.Set;

import org.opennms.netmgt.model.OnmsAlarm;

import com.google.common.collect.Sets;

public class ProblemDomain {

    private final String key;
    private final Set<OnmsAlarm> members = Sets.newLinkedHashSet();

    public ProblemDomain(String key, OnmsAlarm member) {
        this.key = Objects.requireNonNull(key);
        this.members.add(Objects.requireNonNull(member));
    }

    public String getKey() {
        return key;
    }

    public Set<OnmsAlarm> getMembers() {
        return members;
    }

    public void addMember(OnmsAlarm member) {
        members.add(member);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ProblemDomain)) {
            return false;
        }
        ProblemDomain other = (ProblemDomain) obj;
        return Objects.equals(key, other.key);
    }

    @Override
    public String toString() {
        return String.format("ProblemDomain[key=%s, members=%s]",
                key, members);
    }
}
