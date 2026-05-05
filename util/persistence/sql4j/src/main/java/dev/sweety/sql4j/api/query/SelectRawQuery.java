package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import java.util.List;

public non-sealed interface SelectRawQuery extends Query<List<Row>> {
    SelectRawQuery where(Criterion criterion);
    SelectRawQuery select(Column<?>... columns);

    SelectRawQuery limit(int limit);
    SelectRawQuery offset(int offset);
    SelectRawQuery orderBy(String column, boolean ascending);
}
