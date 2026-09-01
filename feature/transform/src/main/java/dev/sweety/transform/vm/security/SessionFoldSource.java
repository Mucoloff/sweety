package dev.sweety.transform.vm.security;

public interface SessionFoldSource {
    boolean fresh();
    long fold(long salt);
}
