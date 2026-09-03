package dev.sweety.sql4j.rpc;

import dev.sweety.data.buffer.NioBuffer;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbQueryRequest;
import dev.sweety.sql4j.rpc.packet.DbQueryResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

public class ZeroCopyRpcDirectStreamTest {

    @Test
    public void testDbQueryRequestZeroCopyNioBufferRoundtrip() {
        String sql = "SELECT id, name, age FROM users WHERE status = ? AND score > ?";
        Object[] params = new Object[]{"ACTIVE", 95.5};
        DbQueryRequest req = new DbQueryRequest(sql, params, true);

        NioBuffer buffer = NioBuffer.heap(256);
        req.write(buffer);

        DbQueryRequest decoded = new DbQueryRequest();
        decoded.read(buffer);

        Assertions.assertEquals(sql, decoded.sql());
        Assertions.assertTrue(decoded.returnGeneratedKeys());
        Assertions.assertEquals(2, decoded.params().length);
        Assertions.assertEquals("ACTIVE", decoded.params()[0]);
        Assertions.assertEquals(95.5, decoded.params()[1]);
    }

    @Test
    public void testDbQueryResponseZeroCopyNioBufferRoundtrip() {
        String[] columns = new String[]{"id", "created_at", "raw_data", "active"};
        Timestamp now = new Timestamp(System.currentTimeMillis());
        byte[] payload = new byte[]{1, 2, 3, 4};
        Object[][] rows = new Object[][]{
                {101, now, payload, true},
                {102, null, new byte[0], false}
        };

        DbQueryResponse resp = new DbQueryResponse(columns, rows);
        NioBuffer buffer = NioBuffer.heap(512);
        resp.write(buffer);

        DbQueryResponse decoded = new DbQueryResponse();
        decoded.read(buffer);

        Assertions.assertNull(decoded.error());
        Assertions.assertArrayEquals(columns, decoded.columns());
        Assertions.assertEquals(2, decoded.rows().length);
        Assertions.assertEquals(101, decoded.rows()[0][0]);
        Assertions.assertEquals(now, decoded.rows()[0][1]);
        Assertions.assertArrayEquals(payload, (byte[]) decoded.rows()[0][2]);
        Assertions.assertEquals(true, decoded.rows()[0][3]);

        Assertions.assertEquals(102, decoded.rows()[1][0]);
        Assertions.assertNull(decoded.rows()[1][1]);
        Assertions.assertArrayEquals(new byte[0], (byte[]) decoded.rows()[1][2]);
        Assertions.assertEquals(false, decoded.rows()[1][3]);
    }

    @Test
    public void testDbMutationZeroCopyNioBufferRoundtrip() {
        DbMutationRequest req = new DbMutationRequest("UPDATE users SET score = ? WHERE id = ?", new Object[]{100.0, 42}, true);
        NioBuffer buffer = NioBuffer.heap(256);
        req.write(buffer);

        DbMutationRequest decodedReq = new DbMutationRequest();
        decodedReq.read(buffer);
        Assertions.assertEquals("UPDATE users SET score = ? WHERE id = ?", decodedReq.sql());
        Assertions.assertEquals(100.0, decodedReq.params()[0]);
        Assertions.assertEquals(42, decodedReq.params()[1]);
        Assertions.assertTrue(decodedReq.returnGeneratedKeys());

        DbMutationResponse resp = new DbMutationResponse(1, 42L);
        NioBuffer respBuf = NioBuffer.heap(256);
        resp.write(respBuf);

        DbMutationResponse decodedResp = new DbMutationResponse();
        decodedResp.read(respBuf);
        Assertions.assertNull(decodedResp.error());
        Assertions.assertEquals(1, decodedResp.updateCount());
        Assertions.assertEquals(42L, decodedResp.generatedKey());
    }

    @Test
    public void testDbBatchMutationZeroCopyNioBufferRoundtrip() {
        String sql = "INSERT INTO logs (level, message) VALUES (?, ?)";
        Object[][] paramRows = new Object[][]{
                {"INFO", "Started"},
                {"WARN", "Memory pressure"},
                {"ERROR", "Failed to connect"}
        };
        DbBatchMutationRequest req = new DbBatchMutationRequest(sql, paramRows);
        NioBuffer buffer = NioBuffer.heap(512);
        req.write(buffer);

        DbBatchMutationRequest decodedReq = new DbBatchMutationRequest();
        decodedReq.read(buffer);
        Assertions.assertEquals(sql, decodedReq.sql());
        Assertions.assertEquals(3, decodedReq.paramRows().length);
        Assertions.assertEquals("INFO", decodedReq.paramRows()[0][0]);
        Assertions.assertEquals("Started", decodedReq.paramRows()[0][1]);

        int[] updateCounts = new int[]{1, 1, 1};
        DbBatchMutationResponse resp = new DbBatchMutationResponse(updateCounts);
        NioBuffer respBuf = NioBuffer.heap(256);
        resp.write(respBuf);

        DbBatchMutationResponse decodedResp = new DbBatchMutationResponse();
        decodedResp.read(respBuf);
        Assertions.assertNull(decodedResp.error());
        Assertions.assertArrayEquals(updateCounts, decodedResp.updateCounts());
    }
}
