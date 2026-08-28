package dev.sweety.patch.archive;

import dev.sweety.patch.model.AddOperation;
import dev.sweety.patch.model.DeleteOperation;
import dev.sweety.patch.model.ModifyOperation;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;
import dev.sweety.patch.model.type.PatchTypes;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PatchArchiveIOTest {

    @Test
    void roundTripArchive_matchesOperations() throws Exception {
        byte[] data = "x".getBytes(StandardCharsets.UTF_8);
        Patch original = new Patch("0.1", "0.2", List.of(
                new AddOperation("n/a.txt", "ab", data),
                new ModifyOperation("b.txt", "cd", data, PatchOperation.Method.REPLACEMENT),
                new DeleteOperation("z.txt", null)
        ));

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        PatchTypes.PATCH_JAR.writer().write(original, raw);

        Patch round = PatchTypes.PATCH_JAR.reader().read(new ByteArrayInputStream(raw.toByteArray()));
        assertEquals("0.1", round.getFromVersion());
        assertEquals("0.2", round.getToVersion());
        assertEquals(3, round.getOperations().size());
        assertInstanceOf(AddOperation.class, round.getOperations().get(0));
        assertInstanceOf(ModifyOperation.class, round.getOperations().get(1));
        assertInstanceOf(DeleteOperation.class, round.getOperations().get(2));
        assertArrayEquals(data, round.getOperations().get(0).data());
    }
}
