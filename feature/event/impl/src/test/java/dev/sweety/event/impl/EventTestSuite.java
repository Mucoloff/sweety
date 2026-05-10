package dev.sweety.event.impl;

import dev.sweety.event.api.EventTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    EventSystemTest.class,
    EventTest.class
})
public class EventTestSuite {
}
