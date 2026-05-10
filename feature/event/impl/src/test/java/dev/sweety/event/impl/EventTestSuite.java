package dev.sweety.event.impl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    EventSystemTest.class
})
public class EventTestSuite {
}
