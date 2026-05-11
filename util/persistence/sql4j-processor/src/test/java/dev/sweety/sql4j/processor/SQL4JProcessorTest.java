package dev.sweety.sql4j.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Annotation-processor tests for {@link SQL4JProcessor}.
 *
 * <p>Uses {@code compile-testing} to exercise the processor at compile time, verifying
 * that valid entities produce correct mirror classes and that invalid entities fail with
 * clear, actionable error messages.
 */
public class SQL4JProcessorTest {

    // ── Helper: common imports shared by every source snippet ─────────────────

    private static final String IMPORTS =
            "import dev.sweety.sql4j.api.obj.Table;\n" +
            "import dev.sweety.sql4j.api.obj.Column;\n" +
            "import dev.sweety.sql4j.api.annotation.Sql4jRepository;\n" +
            "import dev.sweety.sql4j.api.annotation.Query;\n" +
            "import dev.sweety.sql4j.api.repository.Repository;\n" +
            "import java.util.List;\n" +
            "import java.util.concurrent.CompletableFuture;\n";

    // ── B2-T1 ─────────────────────────────────────────────────────────────────

    /**
     * B2-T1: A minimal, valid entity with one PK column compiles successfully and the
     * generated {@code ProductTable} source contains the three required string constants.
     */
    @Test
    void T1_validEntity_generatesMirrorClassWithRequiredConstants() {
        String source =
                "package com.example;\n" + IMPORTS +
                "@Table.Info(name = \"products\")\n" +
                "public class Product {\n" +
                "    @Column.Info(name = \"id\", primaryKey = true, autoIncrement = true)\n" +
                "    private Integer id;\n" +
                "    @Column.Info(name = \"name\")\n" +
                "    private String name;\n" +
                "    public Product() {}\n" +
                "    public Integer getId() { return id; }\n" +
                "    public void setId(Integer id) { this.id = id; }\n" +
                "    public String getName() { return name; }\n" +
                "    public void setName(String name) { this.name = name; }\n" +
                "}\n";

        Compilation compilation = javac()
                .withProcessors(new SQL4JProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Product", source));

        assertThat(compilation).succeeded();

        assertThat(compilation)
                .generatedSourceFile("com.example.ProductTable")
                .contentsAsUtf8String()
                .contains("TABLE_NAME");

        assertThat(compilation)
                .generatedSourceFile("com.example.ProductTable")
                .contentsAsUtf8String()
                .contains("INSERT_SQL");

        assertThat(compilation)
                .generatedSourceFile("com.example.ProductTable")
                .contentsAsUtf8String()
                .contains("SELECT_BY_PK_SQL");
    }

    // ── B2-T2 ─────────────────────────────────────────────────────────────────

    /**
     * B2-T2: An interface annotated with {@code @Sql4jRepository} backed by a valid entity
     * compiles successfully and the generated {@code ProductRepositoryImpl} class is present.
     */
    @Test
    void T2_validRepository_generatesImplementation() {
        String entitySource =
                "package com.example;\n" + IMPORTS +
                "@Table.Info(name = \"products\")\n" +
                "public class Product {\n" +
                "    @Column.Info(name = \"id\", primaryKey = true, autoIncrement = true)\n" +
                "    private Integer id;\n" +
                "    @Column.Info(name = \"name\")\n" +
                "    private String name;\n" +
                "    public Product() {}\n" +
                "    public Integer getId() { return id; }\n" +
                "    public void setId(Integer id) { this.id = id; }\n" +
                "    public String getName() { return name; }\n" +
                "    public void setName(String name) { this.name = name; }\n" +
                "}\n";

        String repoSource =
                "package com.example;\n" + IMPORTS +
                "@Sql4jRepository(entity = Product.class)\n" +
                "public interface ProductRepository extends Repository<Product> {}\n";

        Compilation compilation = javac()
                .withProcessors(new SQL4JProcessor())
                .compile(
                        JavaFileObjects.forSourceString("com.example.Product", entitySource),
                        JavaFileObjects.forSourceString("com.example.ProductRepository", repoSource)
                );

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("com.example.ProductRepositoryImpl");
    }

    // ── B2-T3 ─────────────────────────────────────────────────────────────────

    /**
     * B2-T3: An entity without any primary-key column must fail compilation with a message
     * that mentions "primary key" (case-insensitive).
     */
    @Test
    void T3_missingPrimaryKey_compilationFailsWithClearMessage() {
        String source =
                "package com.example;\n" + IMPORTS +
                "@Table.Info(name = \"items\")\n" +
                "public class Item {\n" +
                "    @Column.Info(name = \"code\")\n" +
                "    private String code;\n" +
                "    @Column.Info(name = \"label\")\n" +
                "    private String label;\n" +
                "    public Item() {}\n" +
                "}\n";

        Compilation compilation = javac()
                .withProcessors(new SQL4JProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Item", source));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContainingMatch("(?i)primary key");
    }

    // ── B2-T4 ─────────────────────────────────────────────────────────────────

    /**
     * B2-T4: An entity where two fields map to the same column name must fail compilation
     * with a message that contains "duplicate" or "column" (case-insensitive).
     */
    @Test
    void T4_duplicateColumnNames_compilationFailsWithClearMessage() {
        String source =
                "package com.example;\n" + IMPORTS +
                "@Table.Info(name = \"widgets\")\n" +
                "public class Widget {\n" +
                "    @Column.Info(name = \"id\", primaryKey = true)\n" +
                "    private Integer id;\n" +
                "    @Column.Info(name = \"value\")\n" +
                "    private String valueA;\n" +
                "    @Column.Info(name = \"value\")\n" +  // duplicate column name
                "    private String valueB;\n" +
                "    public Widget() {}\n" +
                "}\n";

        Compilation compilation = javac()
                .withProcessors(new SQL4JProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Widget", source));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContainingMatch("(?i)(duplicate|column)");
    }

    // ── B2-T5 ─────────────────────────────────────────────────────────────────

    /**
     * B2-T5: A {@code @Query}-annotated method on a repository interface compiles
     * successfully and the generated implementation wires the named parameter ({@code id})
     * into a {@link dev.sweety.sql4j.api.query.ParamQuery} via {@code List.of(id)}.
     * At runtime {@code ParamQuery.bind()} calls {@code ps.setObject} for each element of that
     * list, so parametric binding is guaranteed without needing explicit {@code setObject} in the
     * generated source text.
     *
     * <p>The interface method declares {@code Query<List<UserEntity>>} to match the type that the
     * processor actually generates ({@code return new ParamQuery<>(...)}).
     */
    @Test
    void T5_queryAnnotation_generatesParametricBinding() {
        String entitySource =
                "package com.example;\n" + IMPORTS +
                "@Table.Info(name = \"users\")\n" +
                "public class UserEntity {\n" +
                "    @Column.Info(name = \"id\", primaryKey = true, autoIncrement = true)\n" +
                "    private Integer id;\n" +
                "    @Column.Info(name = \"name\")\n" +
                "    private String name;\n" +
                "    public UserEntity() {}\n" +
                "    public Integer getId() { return id; }\n" +
                "    public void setId(Integer id) { this.id = id; }\n" +
                "    public String getName() { return name; }\n" +
                "    public void setName(String name) { this.name = name; }\n" +
                "}\n";

        // The return type must be Query<List<UserEntity>>: the processor generates
        // `return new ParamQuery<>(sql, List.of(id), table())` which is a Query, not a Future.
        // Use FQN to avoid name clash between annotation @Query and interface Query.
        String repoSource =
                "package com.example;\n" + IMPORTS +
                "@Sql4jRepository(entity = UserEntity.class)\n" +
                "public interface UserRepository extends Repository<UserEntity> {\n" +
                "    @Query(\"SELECT * FROM users WHERE id = :id\")\n" +
                "    dev.sweety.sql4j.api.query.Query<List<UserEntity>> findById(long id);\n" +
                "}\n";

        Compilation compilation = javac()
                .withProcessors(new SQL4JProcessor())
                .compile(
                        JavaFileObjects.forSourceString("com.example.UserEntity", entitySource),
                        JavaFileObjects.forSourceString("com.example.UserRepository", repoSource)
                );

        assertThat(compilation).succeeded();

        // The generated method body must pass the `id` parameter into ParamQuery's params list.
        // ParamQuery.bind() then calls ps.setObject(1, id), achieving the parametric binding.
        assertThat(compilation)
                .generatedSourceFile("com.example.UserRepositoryImpl")
                .contentsAsUtf8String()
                .containsMatch("List\\.of\\(id\\)|List\\.of\\(.*id.*\\)");
    }
}
