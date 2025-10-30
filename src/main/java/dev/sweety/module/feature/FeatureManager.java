package dev.sweety.module.feature;

import dev.sweety.module.loader.DownloadFile;
import dev.sweety.module.loader.SingleClassLoader;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureManager {

    private final Map<String, OptionalFeature> features = new ConcurrentHashMap<>();

    /**
     * Scarica un file .class da un URL, lo carica e ne crea un'istanza.
     *
     * @param url L'URL del file .class.
     * @param className Il nome completo della classe (es. "dev.sweety.full.feature.MyOptionalFeatureImpl").
     * @return Un CompletableFuture che conterrà l'istanza della funzionalità caricata.
     */
    public CompletableFuture<OptionalFeature> loadFeatureFromUrl(String url, String className) {
        // Scarica il file in una locazione temporanea
        return DownloadFile.downloadFromURL(url, className + ".class", false)
                .thenApplyAsync(classFile -> {
                    try {
                        SingleClassLoader loader = new SingleClassLoader(getClass().getClassLoader());
                        Class<?> clazz = loader.loadClassFromFile(classFile, className);

                        if (!OptionalFeature.class.isAssignableFrom(clazz)) {
                            throw new ClassCastException("La classe deve implementare OptionalFeature: " + className);
                        }

                        OptionalFeature feature = (OptionalFeature) clazz.getDeclaredConstructor().newInstance();
                        features.put(feature.getFeatureName(), feature);

                        System.out.println("Funzionalità caricata: " + feature.getFeatureName());
                        classFile.delete(); // Pulisci il file temporaneo
                        return feature;
                    } catch (Exception e) {
                        throw new RuntimeException("Impossibile caricare la funzionalità da " + url, e);
                    }
                });
    }

    public OptionalFeature getFeature(String name) {
        return features.get(name);
    }
}
