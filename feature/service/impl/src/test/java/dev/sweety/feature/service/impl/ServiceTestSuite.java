package dev.sweety.feature.service.impl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    ChildServiceRegistryTest.class,
    DependencyInjectorTest.class,
    ServiceManagerTest.class
})
public class ServiceTestSuite {
}
