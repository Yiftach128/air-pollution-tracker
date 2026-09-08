package com.pollution.datacollector.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShardTest {

    private static final List<String> FIVE = List.of("a", "b", "c", "d", "e");

    @Test
    void theOnlyMemberTakesEverything() {
        assertEquals(FIVE, new Shard(0, 1).select(FIVE));
    }

    @Test
    void eachMemberTakesEveryCountThItemFromItsRank() {
        assertEquals(List.of("a", "d"), new Shard(0, 3).select(FIVE));
        assertEquals(List.of("b", "e"), new Shard(1, 3).select(FIVE));
        assertEquals(List.of("c"), new Shard(2, 3).select(FIVE));
    }

    @Test
    void theSharesOfAGroupCoverTheListExactlyOnceAndDifferInSizeByAtMostOne() {
        for (int count = 1; count <= 7; count++) {
            List<String> covered = new ArrayList<>();
            int smallest = Integer.MAX_VALUE;
            int largest = 0;
            for (int rank = 0; rank < count; rank++) {
                List<String> share = new Shard(rank, count).select(FIVE);
                covered.addAll(share);
                smallest = Math.min(smallest, share.size());
                largest = Math.max(largest, share.size());
            }
            Collections.sort(covered);
            assertEquals(FIVE, covered, "a group of " + count);
            assertTrue(largest - smallest <= 1, "a group of " + count + " splits " + smallest + ".." + largest);
        }
    }

    @Test
    void aMemberRankedBeyondTheListTakesNothing() {
        assertEquals(List.of(), new Shard(3, 4).select(List.of("a", "b")));
    }

    @Test
    void noneTakesNothing() {
        assertEquals(List.of(), Shard.NONE.select(FIVE));
        assertTrue(Shard.NONE.isNone());
        assertFalse(new Shard(0, 1).isNone());
        assertEquals(Shard.NONE, new Shard(0, 0));
    }

    @Test
    void theRankMustBeWithinTheCount() {
        assertThrows(IllegalArgumentException.class, () -> new Shard(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Shard(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Shard(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Shard(1, 0));
    }

    @Test
    void readsAsRankOfCount() {
        assertEquals("1 of 3", new Shard(1, 3).toString());
        assertEquals("none", Shard.NONE.toString());
    }
}
