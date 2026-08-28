package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.Service;
import dev.sweety.feature.service.api.ServiceKey;
import dev.sweety.feature.service.api.ServiceRegistry;
import dev.sweety.feature.service.api.annotation.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChildServiceRegistryTest {

    // ── test service stubs ────────────────────────────────────────────────────

    interface ServiceA {}
    interface ServiceB {}
    interface ServiceC {}
    interface ServiceD {}

    static class ImplA implements ServiceA {}
    static class ImplB implements ServiceB {}
    static class ImplC implements ServiceC {}
    static class ImplD implements ServiceD {}

    /** Service that needs ServiceA injected via DI. */
    static class NeedsA implements ServiceD {
        final ServiceA a;

        @Inject
        NeedsA(ServiceA a) {
            this.a = a;
        }
    }

    static class LifecycleService implements LifecycleServiceIface {
        boolean enabled = false;
        final List<String> log;
        final String id;

        LifecycleService(List<String> log, String id) {
            this.log = log;
            this.id = id;
        }

        @Override public void onEnable()  { enabled = true;  log.add("enable:"  + id); }
        @Override public void onDisable() { enabled = false; log.add("disable:" + id); }
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private ServiceManager parent;
    private ServiceA a;
    private ServiceB b;
    private ServiceC c;

    @BeforeEach
    void setUp() {
        parent = new ServiceManager();
        a = new ImplA();
        b = new ImplB();
        c = new ImplC();
        parent.put(ServiceA.class, a);
        parent.put(ServiceB.class, b);
        parent.put(ServiceC.class, c);
    }

    // ── 1. read: inherited keys visible, non-inherited not ───────────────────

    @Test
    void inheritedKeysAreVisibleInChild() {
        ServiceRegistry child = parent.child(ServiceA.class, ServiceB.class);
        assertSame(a, child.get(ServiceA.class));
        assertSame(b, child.get(ServiceB.class));
    }

    @Test
    void nonInheritedKeyIsNotVisibleInChild() {
        ServiceRegistry child = parent.child(ServiceA.class, ServiceB.class);
        assertNull(child.getOrNull(ServiceC.class));
        assertFalse(child.contains(ServiceC.class));
    }

    // ── 2. own layer: child can put/get/remove its own keys ──────────────────

    @Test
    void childCanPutOwnKey() {
        ServiceRegistry child = parent.child(ServiceA.class);
        ServiceD d = new ImplD();
        child.put(ServiceD.class, d);
        assertSame(d, child.get(ServiceD.class));
    }

    @Test
    void ownKeyNotVisibleInParent() {
        ServiceRegistry child = parent.child(ServiceA.class);
        child.put(ServiceD.class, new ImplD());
        assertFalse(parent.contains(ServiceD.class));
    }

    @Test
    void childCanRemoveOwnKey() {
        ServiceRegistry child = parent.child(ServiceA.class);
        ServiceD d = new ImplD();
        child.put(ServiceD.class, d);
        child.remove(ServiceD.class);
        assertFalse(child.contains(ServiceD.class));
    }

    // ── 3. write-guard on inherited keys ─────────────────────────────────────

    @Test
    void putOnInheritedKeyThrows() {
        ServiceRegistry child = parent.child(ServiceA.class);
        assertThrows(IllegalStateException.class, () -> child.put(ServiceA.class, new ImplA()));
    }

    @Test
    void putProviderOnInheritedKeyThrows() {
        ServiceRegistry child = parent.child(ServiceA.class);
        assertThrows(IllegalStateException.class, () -> child.put(ServiceKey.key(ServiceA.class), (dev.sweety.feature.service.api.Provider<ServiceA>) ImplA::new));
    }

    @Test
    void putIfAbsentOnInheritedKeyThrows() {
        ServiceRegistry child = parent.child(ServiceA.class);
        assertThrows(IllegalStateException.class, () -> child.putIfAbsent(ServiceA.class, new ImplA()));
    }

    @Test
    void removeOnInheritedKeyThrows() {
        ServiceRegistry child = parent.child(ServiceA.class);
        assertThrows(IllegalStateException.class, () -> child.remove(ServiceA.class));
    }

    @Test
    void registerByClassOnInheritedKeyThrows() {
        ServiceRegistry child = parent.child(ServiceA.class);
        assertThrows(IllegalStateException.class, () -> child.registerByClass(ServiceA.class));
    }

    // ── 4. live view: parent mutations reflected in child ────────────────────

    @Test
    void childReflectsParentMutationLive() {
        ServiceRegistry child = parent.child(ServiceA.class);
        ServiceA a2 = new ImplA();
        parent.put(ServiceA.class, a2);
        assertSame(a2, child.get(ServiceA.class));
    }

    // ── 5. registerByClass resolves DI from union (inherited + own) ──────────

    @Test
    void registerByClassCanResolveDependencyFromParent() {
        ServiceRegistry child = parent.child(ServiceA.class); // a is inherited
        NeedsA inst = child.registerByClass(NeedsA.class);
        assertNotNull(inst);
        assertSame(a, inst.a);
        // NeedsA stored in own layer under NeedsA.class key
        assertTrue(child.contains(NeedsA.class));
        assertFalse(parent.contains(NeedsA.class));
    }

    // ── 6. nesting ────────────────────────────────────────────────────────────

    @Test
    void nestedChildOnlySeesIntersection() {
        ServiceRegistry child = parent.child(ServiceA.class, ServiceB.class);
        child.put(ServiceD.class, new ImplD());

        ServiceRegistry grand = child.child(ServiceA.class);
        assertTrue(grand.contains(ServiceA.class));
        assertFalse(grand.contains(ServiceB.class)); // not selected in grandchild
        assertFalse(grand.contains(ServiceD.class)); // own of child, not inherited by grandchild
    }

    // ── 7. close does not affect parent ──────────────────────────────────────

    interface LifecycleServiceIface extends Service {}

    @Test
    void closingChildDoesNotDisableParentServices() {
        List<String> log = new ArrayList<>();
        LifecycleService ls = new LifecycleService(log, "parentLife");
        // use a fresh manager so LifecycleService can be registered under its own interface
        ServiceManager parentLife = new ServiceManager();
        parentLife.put(LifecycleServiceIface.class, (LifecycleServiceIface) ls);
        log.clear();

        ServiceRegistry child = parentLife.child(LifecycleServiceIface.class);
        assertDoesNotThrow(() -> ((AutoCloseable) child).close());

        assertFalse(log.contains("disable:parentLife"));
        assertTrue(ls.enabled);
        parentLife.close();
    }

    @Test
    void closingChildDisablesOwnServices() {
        List<String> log = new ArrayList<>();
        LifecycleService ls = new LifecycleService(log, "childOwn");
        ServiceRegistry child = parent.child(ServiceA.class);
        child.put(LifecycleServiceIface.class, (LifecycleServiceIface) ls);
        assertTrue(ls.enabled);

        assertDoesNotThrow(() -> ((AutoCloseable) child).close());
        assertFalse(ls.enabled);
        assertTrue(log.contains("disable:childOwn"));
    }

    // ── 8. keySet / providers include union ──────────────────────────────────

    @Test
    void keySetIsUnionOfOwnAndInherited() {
        ServiceRegistry child = parent.child(ServiceA.class, ServiceB.class);
        child.put(ServiceD.class, new ImplD());

        var keys = child.keySet();
        assertTrue(keys.contains(ServiceKey.key(ServiceA.class)));
        assertTrue(keys.contains(ServiceKey.key(ServiceB.class)));
        assertTrue(keys.contains(ServiceKey.key(ServiceD.class)));
        assertFalse(keys.contains(ServiceKey.key(ServiceC.class)));
    }

    @Test
    void providersCountMatchesUnion() {
        ServiceRegistry child = parent.child(ServiceA.class, ServiceB.class);
        child.put(ServiceD.class, new ImplD());
        assertEquals(3, child.providers().size()); // A + B + D
    }

    // ── 9. predicate overload ─────────────────────────────────────────────────

    @Test
    void predicateOverloadSelectsByType() {
        // inherit only ServiceKey whose type is ServiceA
        ServiceRegistry child = parent.child(k -> k.type() == ServiceA.class);
        assertTrue(child.contains(ServiceA.class));
        assertFalse(child.contains(ServiceB.class));
    }
}
