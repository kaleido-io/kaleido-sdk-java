// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import io.kaleido.sdk.core.JSON;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataModelQueryTest {

    private static String json(Object query) throws Exception {
        return JSON.MAPPER.writeValueAsString(query);
    }

    @Test
    void anEmptyQueryIsAnEmptyObject() throws Exception {
        assertEquals("{}", json(DataModelQuery.create()));
    }

    @Test
    void eachComparisonAddsATermUnderItsOperator() throws Exception {
        var query = DataModelQuery.create()
                .eq("a", "1").eq("b", "2").neq("c", "3").contains("d", "4").startsWith("e", "5").endsWith("f", "6")
                .lt("g", "7").lte("h", "8").gt("i", "9").gte("j", "10");

        assertEquals("{\"eq\":[{\"field\":\"a\",\"value\":\"1\"},{\"field\":\"b\",\"value\":\"2\"}],"
                + "\"neq\":[{\"field\":\"c\",\"value\":\"3\"}],\"contains\":[{\"field\":\"d\",\"value\":\"4\"}],"
                + "\"startsWith\":[{\"field\":\"e\",\"value\":\"5\"}],\"endsWith\":[{\"field\":\"f\",\"value\":\"6\"}],"
                + "\"lt\":[{\"field\":\"g\",\"value\":\"7\"}],\"lte\":[{\"field\":\"h\",\"value\":\"8\"}],"
                + "\"gt\":[{\"field\":\"i\",\"value\":\"9\"}],\"gte\":[{\"field\":\"j\",\"value\":\"10\"}]}", json(query));
    }

    @Test
    void setsNullsLabelsAndOptions() throws Exception {
        var query = DataModelQuery.create()
                .in("type", "mint", "burn").notIn("to", "0x0").isNull("signer").label("env", "prod")
                .where("eq", "name", "Bond", true, true)
                .skip(20).limit(10).sort("-created", "name").count(true).fields("id", "name");

        assertEquals("{\"in\":[{\"field\":\"type\",\"values\":[\"mint\",\"burn\"]}],"
                + "\"nin\":[{\"field\":\"to\",\"values\":[\"0x0\"]}],\"null\":[{\"field\":\"signer\"}],"
                + "\"labels\":{\"eq\":[{\"field\":\"env\",\"value\":\"prod\"}]},"
                + "\"eq\":[{\"field\":\"name\",\"value\":\"Bond\",\"not\":true,\"caseInsensitive\":true}],"
                + "\"skip\":20,\"limit\":10,\"sort\":[\"-created\",\"name\"],\"count\":true,\"fields\":[\"id\",\"name\"]}",
                json(query));
    }

    @Test
    void orTakesACopyOfEachAlternative() throws Exception {
        var mint = DataModelQuery.create().eq("type", "mint");
        var query = DataModelQuery.create().or(mint, DataModelQuery.create().eq("type", "burn"));
        mint.eq("late", "change");

        assertEquals("{\"or\":[{\"eq\":[{\"field\":\"type\",\"value\":\"mint\"}]},"
                + "{\"eq\":[{\"field\":\"type\",\"value\":\"burn\"}]}]}", json(query));
        assertEquals(query.toJson().toString(), query.toString());
    }
}
