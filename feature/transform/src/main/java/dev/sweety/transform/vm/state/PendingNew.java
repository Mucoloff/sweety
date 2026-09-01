package dev.sweety.transform.vm.state;

public final class PendingNew {

    public final String internalClassName;
    public Object resolved;

    public PendingNew(String internalClassName) {
        this.internalClassName = internalClassName;
    }

    public static Object unwrap(Object v) {
        return v instanceof PendingNew pending && pending.resolved != null ? pending.resolved : v;
    }
}
