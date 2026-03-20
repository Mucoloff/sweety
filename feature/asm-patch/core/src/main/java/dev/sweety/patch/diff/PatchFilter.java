package dev.sweety.patch.diff;

public interface PatchFilter {
    boolean exclude(String path);

    // Combinazione OR: esclude se uno dei due filtri dice true
    default PatchFilter or(PatchFilter other) {
        return path -> this.exclude(path) || other.exclude(path);
    }

    // Combinazione AND: esclude solo se entrambi i filtri dicono true
    default PatchFilter and(PatchFilter other) {
        return path -> this.exclude(path) && other.exclude(path);
    }

    // Negazione: esclude se questo filtro NON esclude
    default PatchFilter not() {
        return path -> !this.exclude(path);
    }

    // Combina una lista di filtri con OR
    static PatchFilter anyOf(PatchFilter... filters) {
        return path -> {
            for (PatchFilter f : filters) {
                if (f.exclude(path)) return true;
            }
            return false;
        };
    }

    // Combina una lista di filtri con AND
    static PatchFilter allOf(PatchFilter... filters) {
        return path -> {
            for (PatchFilter f : filters) {
                if (!f.exclude(path)) return false;
            }
            return true;
        };
    }
}