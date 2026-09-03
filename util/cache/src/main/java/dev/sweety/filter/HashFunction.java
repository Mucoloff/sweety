package dev.sweety.filter;

/**
 * Una singola funzione hash su {@code byte[]}.
 * Passare più istanze (es. con seed diversi) ai costruttori di sketch e bloom filter.
 */
@FunctionalInterface
public interface HashFunction {

    /**
     * @param data dati da hashare; non modificare l'array nell'implementazione salvo stipulazione contraria
     * @return valore hash (può essere qualsiasi {@code int}; il chiamante applica il modulo sulla dimensione)
     */
    int hash(byte[] data);
}
