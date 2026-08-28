package dev.sweety.sql4j.benchmark;

import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.benchmark.entity.BenchProject;
import dev.sweety.sql4j.benchmark.entity.BenchProjectTable;
import dev.sweety.sql4j.benchmark.entity.BenchTask;
import dev.sweety.sql4j.benchmark.entity.BenchTaskTable;
import dev.sweety.sql4j.benchmark.entity.BenchUser;
import dev.sweety.sql4j.benchmark.entity.BenchUserTable;
import dev.sweety.sql4j.impl.Database;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Compares two ways of reading a {@code User -> N Projects -> N Tasks} hierarchy:
 * <ul>
 *   <li>{@code eagerJoin} — one query, {@code .fetch(PROJECTS_REL, TASKS_REL)}, hydrated via
 *       {@code SelectJoin.mapToHierarchy}'s in-memory identity-map join collapse
 *       (flagged O(n·m) nested scan per relation during exploration).</li>
 *   <li>{@code lazyNPlusOne} — base select, then one extra query per user for its projects and
 *       one per project for its tasks — the naive N+1 pattern the EAGER path exists to avoid.</li>
 * </ul>
 * {@code usersPerProject}/{@code tasksPerProject} are fixed at 1 user with {@code projectCount}
 * projects, each with {@code tasksPerProject} tasks, so the relation fan-out is controlled by
 * a single {@code @Param}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class RelationJoinBenchmark {

    @Param({"5", "20", "50"})
    int projectCount;

    static final int TASKS_PER_PROJECT = 5;

    private Database db;
    private SqlConnection con;
    private Repository<BenchUser> users;
    private Repository<BenchProject> projects;
    private Repository<BenchTask> tasks;
    private Integer ownerId;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String dbPath = "mem:relations_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        db = SQL4J.connect().h2(dbPath, "sa", "").open();
        con = db.getConnection();
        users = db.createRepository(BenchUser.class);
        projects = db.createRepository(BenchProject.class);
        tasks = db.createRepository(BenchTask.class);
        users.createTable().execute(con).join();
        projects.createTable().execute(con).join();
        tasks.createTable().execute(con).join();

        BenchUser owner = new BenchUser();
        owner.setName("owner");
        users.insert(owner).execute(con).join();
        ownerId = owner.getId();

        for (int p = 0; p < projectCount; p++) {
            BenchProject project = new BenchProject();
            project.setTitle("project-" + p);
            project.setOwner(owner);
            projects.insert(project).execute(con).join();
            for (int t = 0; t < TASKS_PER_PROJECT; t++) {
                BenchTask task = new BenchTask();
                task.setDescription("task-" + p + "-" + t);
                task.setProject(project);
                tasks.insert(task).execute(con).join();
            }
        }
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try { tasks.dropTable().execute(con).join(); } catch (Exception ignored) {}
        try { projects.dropTable().execute(con).join(); } catch (Exception ignored) {}
        try { users.dropTable().execute(con).join(); } catch (Exception ignored) {}
        db.close();
    }

    /** One query, joined + hierarchy-mapped server-side result set. */
    @Benchmark
    public void eagerJoin(Blackhole bh) {
        List<BenchUser> hierarchy = users.select()
                .fetch(BenchUserTable.PROJECTS_REL, BenchProjectTable.TASKS_REL)
                .execute(con).join();
        bh.consume(hierarchy);
    }

    /** Base select + one extra query per project for its owner-scoped projects/tasks (N+1). */
    @Benchmark
    public void lazyNPlusOne(Blackhole bh) {
        BenchUser owner = users.pk(ownerId).find().execute(con).join();
        List<BenchProject> ownedProjects = projects.select()
                .where(BenchProjectTable.OWNER.eq(owner.getId()))
                .execute(con).join();
        for (BenchProject project : ownedProjects) {
            List<BenchTask> projectTasks = tasks.select()
                    .where(BenchTaskTable.PROJECT.eq(project.getId()))
                    .execute(con).join();
            bh.consume(projectTasks);
        }
        bh.consume(ownedProjects);
    }
}
