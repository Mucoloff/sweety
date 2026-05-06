package dev.sweety.sql4j.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BuilderTest {

    @Test
    void testUserBuilder() {
        User user = UserTable.builder()
                .name("BuilderTest")
                .age(40)
                .role(Role.ADMIN)
                .build();

        assertEquals("BuilderTest", user.getName());
        assertEquals(40, user.getAge());
        assertEquals(Role.ADMIN, user.getRole());
    }
}
