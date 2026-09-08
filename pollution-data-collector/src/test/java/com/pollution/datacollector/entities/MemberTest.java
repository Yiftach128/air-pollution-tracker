package com.pollution.datacollector.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pollution.common.json.JsonSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MemberTest {

    private static final Instant T0 = Instant.parse("2026-09-07T10:00:00Z");

    @Test
    void needsANonBlankIdAndAJoinTime() {
        assertThrows(IllegalArgumentException.class, () -> new Member(" ", T0));
        assertThrows(NullPointerException.class, () -> new Member(null, T0));
        assertThrows(NullPointerException.class, () -> new Member("pod-7-1", null));
    }

    @Test
    void survivesTheRoundTripThroughJson() {
        Member member = new Member("pod-7-1", T0);

        assertEquals(member, JsonSupport.fromJson(JsonSupport.toJson(member), Member.class));
    }
}
