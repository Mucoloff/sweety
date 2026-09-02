package dev.sweety.transform.transformers.remap;

import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Remaps class internal names into flattened, single-letter or short packages (e.g. 'a/', 'b/')
 * with homoglyph/confusable class names (e.g. 'a/IllIIlIl', 'a/llIlIllI').
 */
public final class ClassRemapAndFlattenTransformer extends Transformer {

    private final Map<String, String> mappings;
    private final String targetPackage;
    private final ConfusableDictionary dictionary;
    private final int nameLength;

    public ClassRemapAndFlattenTransformer() {
        this("a", ConfusableDictionary.ILL, 8);
    }

    public ClassRemapAndFlattenTransformer(String targetPackage, ConfusableDictionary dictionary, int nameLength) {
        this.targetPackage = targetPackage;
        this.dictionary = dictionary;
        this.nameLength = nameLength;
        this.mappings = new HashMap<>();
    }

    public ClassRemapAndFlattenTransformer(Map<String, String> explicitMappings) {
        this.mappings = new HashMap<>(explicitMappings);
        this.targetPackage = "a";
        this.dictionary = ConfusableDictionary.ILL;
        this.nameLength = 8;
    }

    public void registerMapping(String originalInternalName, String newInternalName) {
        mappings.put(originalInternalName.replace('.', '/'), newInternalName.replace('.', '/'));
    }

    public String computeNewName(String originalInternalName, int index) {
        String baseName = ConfusableNameGenerator.generate(index, dictionary, nameLength);
        String prefix = targetPackage.isEmpty() ? "" : targetPackage + "/";
        return prefix + baseName;
    }

    @Override
    public String name() {
        return "ClassRemapAndFlatten";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode originalNode = ctx.classNode();
        String currentName = originalNode.name;

        String mappedName = mappings.get(currentName);
        if (mappedName == null) {
            mappedName = computeNewName(currentName, mappings.size());
            mappings.put(currentName, mappedName);
        }

        SimpleRemapper remapper = new SimpleRemapper(mappings);
        ClassWriter tempWriter = new ClassWriter(0);
        ClassRemapper classRemapper = new ClassRemapper(tempWriter, remapper);
        originalNode.accept(classRemapper);

        byte[] remappedBytes = tempWriter.toByteArray();
        ClassNode remappedNode = new ClassNode();
        new ClassReader(remappedBytes).accept(remappedNode, ClassReader.EXPAND_FRAMES);

        // Replace class node content with remapped node
        originalNode.version = remappedNode.version;
        originalNode.access = remappedNode.access;
        originalNode.name = remappedNode.name;
        originalNode.signature = remappedNode.signature;
        originalNode.superName = remappedNode.superName;
        originalNode.interfaces = remappedNode.interfaces;
        originalNode.sourceFile = remappedNode.sourceFile;
        originalNode.sourceDebug = remappedNode.sourceDebug;
        originalNode.fields = remappedNode.fields;
        originalNode.methods = remappedNode.methods;
        originalNode.innerClasses = remappedNode.innerClasses;
        originalNode.nestHostClass = remappedNode.nestHostClass;
        originalNode.nestMembers = remappedNode.nestMembers;
        originalNode.visibleAnnotations = remappedNode.visibleAnnotations;
        originalNode.invisibleAnnotations = remappedNode.invisibleAnnotations;
    }

    public Map<String, String> getMappings() {
        return mappings;
    }
}
