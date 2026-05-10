package dev.sweety.feature.service.impl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    DependencyInjectorTest.class,
    ServiceManagerTest.class
})
public class ServiceTestSuite {
}
