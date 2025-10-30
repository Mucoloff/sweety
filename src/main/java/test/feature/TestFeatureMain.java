package test.feature;

import dev.sweety.module.feature.FeatureManager;
import dev.sweety.module.feature.OptionalFeature;

import java.io.IOException;

public class TestFeatureMain {

    public static void main(String[] args)  {
        try {
            SimpleFileServer.main(args);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FeatureManager manager = new FeatureManager();

        // Esempio: sostituire con l'URL reale del file .class
        String url = "http://localhost:8080/TestFeature.class";
        String className = "dev.sweety.test.feature.TestFeature";

        manager.loadFeatureFromUrl(url, className)
                .thenAccept(feature -> {
                    // Esegui la feature appena caricata
                    feature.execute();

                    // Recupera la stessa feature via nome
                    OptionalFeature f = manager.getFeature(feature.getFeatureName());
                    if (f != null) {
                        System.out.println("Recuperata feature: " + f.getFeatureName());
                    }
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                })
                .join(); // aspetta il completamento per esempio CLI
    }

}
