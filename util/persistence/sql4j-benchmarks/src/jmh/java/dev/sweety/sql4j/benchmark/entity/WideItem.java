package dev.sweety.sql4j.benchmark.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;

/**
 * A wide (many-column, mixed primitive/boxed) entity used by
 * {@link dev.sweety.sql4j.benchmark.HydrationBenchmark} to stress
 * {@code SelectEntity.mapRow}'s per-column {@code ResultSet.getObject} boxing and the
 * generated dispatcher's {@code get_/set_} calls — a single-int {@link BenchItem} row is too
 * narrow to show that cost.
 */
@Table.Info(name = "wide_items")
public class WideItem {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "c1") private int c1;
    @Column.Info(name = "c2") private int c2;
    @Column.Info(name = "c3") private long c3;
    @Column.Info(name = "c4") private long c4;
    @Column.Info(name = "c5") private double c5;
    @Column.Info(name = "c6") private double c6;
    @Column.Info(name = "c7") private boolean c7;
    @Column.Info(name = "c8") private boolean c8;
    @Column.Info(name = "s1") private String s1;
    @Column.Info(name = "s2") private String s2;
    @Column.Info(name = "s3") private String s3;
    @Column.Info(name = "s4") private String s4;
    @Column.Info(name = "s5") private String s5;
    @Column.Info(name = "s6") private String s6;

    public WideItem() {}

    public static WideItem sample(int seed) {
        WideItem w = new WideItem();
        w.c1 = seed; w.c2 = seed * 2; w.c3 = seed * 3L; w.c4 = seed * 4L;
        w.c5 = seed * 1.5; w.c6 = seed * 2.5; w.c7 = seed % 2 == 0; w.c8 = seed % 3 == 0;
        w.s1 = "s1-" + seed; w.s2 = "s2-" + seed; w.s3 = "s3-" + seed;
        w.s4 = "s4-" + seed; w.s5 = "s5-" + seed; w.s6 = "s6-" + seed;
        return w;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public int getC1() { return c1; } public void setC1(int v) { c1 = v; }
    public int getC2() { return c2; } public void setC2(int v) { c2 = v; }
    public long getC3() { return c3; } public void setC3(long v) { c3 = v; }
    public long getC4() { return c4; } public void setC4(long v) { c4 = v; }
    public double getC5() { return c5; } public void setC5(double v) { c5 = v; }
    public double getC6() { return c6; } public void setC6(double v) { c6 = v; }
    public boolean isC7() { return c7; } public void setC7(boolean v) { c7 = v; }
    public boolean isC8() { return c8; } public void setC8(boolean v) { c8 = v; }
    public String getS1() { return s1; } public void setS1(String v) { s1 = v; }
    public String getS2() { return s2; } public void setS2(String v) { s2 = v; }
    public String getS3() { return s3; } public void setS3(String v) { s3 = v; }
    public String getS4() { return s4; } public void setS4(String v) { s4 = v; }
    public String getS5() { return s5; } public void setS5(String v) { s5 = v; }
    public String getS6() { return s6; } public void setS6(String v) { s6 = v; }
}
