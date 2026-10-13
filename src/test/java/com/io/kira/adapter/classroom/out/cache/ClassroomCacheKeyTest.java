package com.io.kira.adapter.classroom.out.cache;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClassroomCacheKeyTest {

    @Test
    void classroomEntityAndAggregateUseDistinctCacheKeys() {
        ClassroomCacheKey keys = new ClassroomCacheKey();
        UUID classroomId = UUID.randomUUID();

        assertNotEquals(keys.byDomainId(classroomId), keys.byAggregateId(classroomId));
    }
}
