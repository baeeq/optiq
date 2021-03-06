/*
// Licensed to Julian Hyde under one or more contributor license
// agreements. See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.
//
// Julian Hyde licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
package net.hydromatic.optiq.test;

import net.hydromatic.linq4j.function.Function1;

import net.hydromatic.optiq.MutableSchema;
import net.hydromatic.optiq.impl.java.ReflectiveSchema;
import net.hydromatic.optiq.impl.jdbc.JdbcQueryProvider;
import net.hydromatic.optiq.jdbc.OptiqConnection;
import net.hydromatic.optiq.runtime.Hook;

import junit.framework.Assert;
import junit.framework.TestSuite;

import org.eigenbase.sql.parser.SqlParserTest;
import org.eigenbase.sql.test.SqlOperatorTest;
import org.eigenbase.test.*;
import org.eigenbase.util.Util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Fluid DSL for testing Optiq connections and queries.
 *
 * @author jhyde
 */
public class OptiqAssert {
    private static final DateFormat UTC_DATE_FORMAT;
    private static final DateFormat UTC_TIME_FORMAT;
    private static final DateFormat UTC_TIMESTAMP_FORMAT;
    static {
        final TimeZone utc = TimeZone.getTimeZone("UTC");
        UTC_DATE_FORMAT = new SimpleDateFormat("YYYY-MM-dd");
        UTC_DATE_FORMAT.setTimeZone(utc);
        UTC_TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
        UTC_TIME_FORMAT.setTimeZone(utc);
        UTC_TIMESTAMP_FORMAT = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss'Z'");
        UTC_TIMESTAMP_FORMAT.setTimeZone(utc);
    }

    public static AssertThat assertThat() {
        return new AssertThat(Config.REGULAR);
    }

    /** Returns a {@link TestSuite junit suite} of all Optiq tests. */
    public static TestSuite suite() {
        TestSuite testSuite = new TestSuite();
        testSuite.addTestSuite(JdbcTest.class);
        testSuite.addTestSuite(ReflectiveSchemaTest.class);
        testSuite.addTestSuite(LinqFrontJdbcBackTest.class);
        testSuite.addTestSuite(JdbcFrontLinqBackTest.class);
        testSuite.addTestSuite(JdbcFrontJdbcBackLinqMiddleTest.class);
        testSuite.addTestSuite(JdbcFrontJdbcBackTest.class);
        testSuite.addTestSuite(SqlToRelConverterTest.class);
        testSuite.addTestSuite(SqlFunctionsTest.class);
        testSuite.addTestSuite(SqlOperatorTest.class);
        testSuite.addTestSuite(OptiqSqlOperatorTest.class);
        testSuite.addTestSuite(SqlParserTest.class);
        testSuite.addTestSuite(ModelTest.class);
        testSuite.addTestSuite(RexProgramTest.class);
        testSuite.addTestSuite(RexTransformerTest.class);
        //testSuite.addTestSuite(VolcanoPlannerTraitTest.class);
        return testSuite;
    }

    static Function1<Throwable, Void> checkException(
        final String expected)
    {
        return new Function1<Throwable, Void>() {
            public Void apply(Throwable p0) {
                Assert.assertNotNull(
                    "expected exception but none was thrown", p0);
                StringWriter stringWriter = new StringWriter();
                PrintWriter printWriter = new PrintWriter(stringWriter);
                p0.printStackTrace(printWriter);
                printWriter.flush();
                String stack = stringWriter.toString();
                Assert.assertTrue(stack, stack.contains(expected));
                return null;
            }
        };
    }

    static Function1<String, Void> checkResult(final String expected) {
        return new Function1<String, Void>() {
            public Void apply(String p0) {
                Assert.assertEquals(expected, p0);
                return null;
            }
        };
    }

    public static Function1<String, Void> checkResultContains(
        final String expected)
    {
        return new Function1<String, Void>() {
            public Void apply(String p0) {
                Assert.assertTrue(p0, p0.contains(expected));
                return null;
            }
        };
    }

    static void assertQuery(
        Connection connection,
        String sql,
        Function1<String, Void> resultChecker,
        Function1<Throwable, Void> exceptionChecker)
        throws Exception
    {
        Statement statement = connection.createStatement();
        ResultSet resultSet;
        try {
            resultSet = statement.executeQuery(sql);
            if (exceptionChecker != null) {
                exceptionChecker.apply(null);
                return;
            }
        } catch (Exception e) {
            if (exceptionChecker != null) {
                exceptionChecker.apply(e);
                return;
            }
            throw e;
        } catch (Error e) {
            if (exceptionChecker != null) {
                exceptionChecker.apply(e);
                return;
            }
            throw e;
        }
        StringBuilder buf = new StringBuilder();
        while (resultSet.next()) {
            int n = resultSet.getMetaData().getColumnCount();
            if (n > 0) {
                for (int i = 1;; i++) {
                    buf.append(resultSet.getMetaData().getColumnLabel(i))
                        .append("=")
                        .append(str(resultSet, i));
                    if (i == n) {
                        break;
                    }
                    buf.append("; ");
                }
            }
            buf.append("\n");
        }
        resultSet.close();
        statement.close();
        connection.close();

        if (resultChecker != null) {
            resultChecker.apply(buf.toString());
        }
    }

    private static String str(ResultSet resultSet, int i) throws SQLException {
        final int columnType = resultSet.getMetaData().getColumnType(i);
        switch (columnType) {
        case Types.DATE:
            final Date date = resultSet.getDate(i, null);
            return date == null ? "null" : UTC_DATE_FORMAT.format(date);
        case Types.TIME:
            final Time time = resultSet.getTime(i, null);
            return time == null ? "null" : UTC_TIME_FORMAT.format(time);
        case Types.TIMESTAMP:
            final Timestamp timestamp = resultSet.getTimestamp(i, null);
            return timestamp == null
                ? "null" : UTC_TIMESTAMP_FORMAT.format(timestamp);
        default:
            return String.valueOf(resultSet.getObject(i));
        }
    }

    /**
     * Result of calling {@link OptiqAssert#assertThat}.
     */
    public static class AssertThat {
        private final ConnectionFactory connectionFactory;

        private AssertThat(Config config) {
            this(new ConfigConnectionFactory(config));
        }

        private AssertThat(ConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        public AssertThat with(Config config) {
            return new AssertThat(config);
        }

        public AssertThat with(ConnectionFactory connectionFactory) {
            return new AssertThat(connectionFactory);
        }

        /** Sets the default schema to a reflective schema based on a given
         * object. */
        public AssertThat with(final String name, final Object schema) {
            return with(
                new OptiqAssert.ConnectionFactory() {
                    public OptiqConnection createConnection() throws Exception {
                        Class.forName("net.hydromatic.optiq.jdbc.Driver");
                        Connection connection =
                            DriverManager.getConnection("jdbc:optiq:");
                        OptiqConnection optiqConnection =
                            connection.unwrap(OptiqConnection.class);
                        MutableSchema rootSchema =
                            optiqConnection.getRootSchema();
                        ReflectiveSchema.create(rootSchema, name, schema);
                        optiqConnection.setSchema(name);
                        return optiqConnection;
                    }
                });
        }

        public AssertThat withModel(final String model) {
            return new AssertThat(
                new OptiqAssert.ConnectionFactory() {
                    public OptiqConnection createConnection() throws Exception {
                        Class.forName("net.hydromatic.optiq.jdbc.Driver");
                        final Properties info = new Properties();
                        info.setProperty("model", "inline:" + model);
                        return (OptiqConnection) DriverManager.getConnection(
                            "jdbc:optiq:", info);
                    }
                });
        }

        public AssertQuery query(String sql) {
            System.out.println(sql);
            return new AssertQuery(connectionFactory, sql);
        }

        /** Asserts that there is an exception with the given message while
         * creating a connection. */
        public void connectThrows(String message) {
            connectThrows(checkException(message));
        }

        /** Asserts that there is an exception that matches the given predicate
         * while creating a connection. */
        public void connectThrows(
            Function1<Throwable, Void> exceptionChecker)
        {
            Throwable throwable;
            try {
                Connection x = connectionFactory.createConnection();
                try {
                    x.close();
                } catch (SQLException e) {
                    // ignore
                }
                throwable = null;
            } catch (Throwable e) {
                throwable = e;
            }
            exceptionChecker.apply(throwable);
        }

        /** Creates a connection and executes a callback. */
        public <T> T doWithConnection(Function1<OptiqConnection, T> fn)
            throws Exception
        {
            Connection connection = connectionFactory.createConnection();
            try {
                return fn.apply((OptiqConnection) connection);
            } finally {
                connection.close();
            }
        }

        public AssertThat withSchema(String schema) {
            return new AssertThat(
                new SchemaConnectionFactory(connectionFactory, schema));
        }
    }

    public interface ConnectionFactory {
        OptiqConnection createConnection() throws Exception;
    }

    private static class ConfigConnectionFactory implements ConnectionFactory {
        private final Config config;

        public ConfigConnectionFactory(Config config) {
            this.config = config;
        }

        public OptiqConnection createConnection() throws Exception {
            switch (config) {
            case REGULAR:
                return JdbcTest.getConnection("hr", "foodmart");
            case REGULAR_PLUS_METADATA:
                return JdbcTest.getConnection("hr", "foodmart", "metadata");
            case JDBC_FOODMART2:
                return JdbcTest.getConnection(null, false);
            case JDBC_FOODMART:
                return JdbcTest.getConnection(
                    JdbcQueryProvider.INSTANCE, false);
            case FOODMART_CLONE:
                return JdbcTest.getConnection(JdbcQueryProvider.INSTANCE, true);
            default:
                throw Util.unexpected(config);
            }
        }
    }

    private static class DelegatingConnectionFactory
        implements ConnectionFactory
    {
        private final ConnectionFactory factory;

        public DelegatingConnectionFactory(ConnectionFactory factory) {
            this.factory = factory;
        }

        public OptiqConnection createConnection() throws Exception {
            return factory.createConnection();
        }
    }

    private static class SchemaConnectionFactory
        extends DelegatingConnectionFactory
    {
        private final String schema;

        public SchemaConnectionFactory(ConnectionFactory factory, String schema)
        {
            super(factory);
            this.schema = schema;
        }

        @Override
        public OptiqConnection createConnection() throws Exception {
            OptiqConnection connection = super.createConnection();
            connection.setSchema(schema);
            return connection;
        }
    }

    public static class AssertQuery {
        private final String sql;
        private ConnectionFactory connectionFactory;
        private String plan;

        private AssertQuery(ConnectionFactory connectionFactory, String sql) {
            this.sql = sql;
            this.connectionFactory = connectionFactory;
        }

        protected Connection createConnection() throws Exception {
            return connectionFactory.createConnection();
        }

        public AssertQuery returns(String expected) {
            return returns(checkResult(expected));
        }

        public AssertQuery returns(Function1<String, Void> checker) {
            try {
                assertQuery(
                    createConnection(), sql, checker, null);
                return this;
            } catch (Exception e) {
                throw new RuntimeException(
                    "exception while executing [" + sql + "]", e);
            }
        }

        public AssertQuery throws_(String message) {
            try {
                assertQuery(
                    createConnection(), sql, null, checkException(message));
                return this;
            } catch (Exception e) {
                throw new RuntimeException(
                    "exception while executing [" + sql + "]", e);
            }
        }

        public AssertQuery runs() {
            try {
                assertQuery(createConnection(), sql, null, null);
                return this;
            } catch (Exception e) {
                throw new RuntimeException(
                    "exception while executing [" + sql + "]", e);
            }
        }

        public AssertQuery planContains(String expected) {
            ensurePlan();
            Assert.assertTrue(
                "Plan [" + plan + "] contains [" + expected + "]",
                plan.contains(expected));
            return this;
        }

        private void ensurePlan() {
            if (plan != null) {
                return;
            }
            final Hook.Closeable hook = Hook.JAVA_PLAN.add(
                new Function1<Object, Object>() {
                    public Object apply(Object a0) {
                        plan = (String) a0;
                        return null;
                    }
                });
            try {
                assertQuery(createConnection(), sql, null, null);
                Assert.assertNotNull(plan);
            } catch (Exception e) {
                throw new RuntimeException(
                    "exception while executing [" + sql + "]", e);
            } finally {
                hook.close();
            }
        }
    }

    public enum Config {
        /**
         * Configuration that creates a connection with two in-memory data sets:
         * {@link net.hydromatic.optiq.test.JdbcTest.HrSchema} and
         * {@link net.hydromatic.optiq.test.JdbcTest.FoodmartSchema}.
         */
        REGULAR,

        /**
         * Configuration that creates a connection to a MySQL server. Tables
         * such as "customer" and "sales_fact_1997" are available. Queries
         * are processed by generating Java that calls linq4j operators
         * such as
         * {@link net.hydromatic.linq4j.Enumerable#where(net.hydromatic.linq4j.function.Predicate1)}.
         */
        JDBC_FOODMART,
        JDBC_FOODMART2,

        /** Configuration that contains an in-memory clone of the FoodMart
         * database. */
        FOODMART_CLONE,

        /** Configuration that includes the metadata schema. */
        REGULAR_PLUS_METADATA,
    }
}

// End OptiqAssert.java
