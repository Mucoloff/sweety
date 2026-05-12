package dev.sweety.filter;

/**
 * Interfaccia per strategie di hash personalizzate.
 * Genera due hash base da cui le implementazioni di filtri
 * derivano k hash usando Kirsch-Mitzenmacher.
 */
public interface HashFunction {
    /**
     * Primo hash della sequenza.
     * @param data i dati da hashare
     * @return valore hash (può essere negativo)
     */
    int hash1(byte[] data);

    /**
     * Secondo hash della sequenza.
     * @param data i dati da hashare
     * @return valore hash (può essere negativo)
     */
    int hash2(byte[] data);
}

