/*
 * Copyright 2019-2020 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.restapi;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kong.unirest.HttpRequest;
import kong.unirest.HttpResponse;
import kong.unirest.RequestBodyEntity;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.polypheny.db.TestHelper;
import org.polypheny.db.TestHelper.JdbcConnection;

@Slf4j
public class RestTest {

    @BeforeClass
    public static void start() throws SQLException {
        // Ensures that Polypheny-DB is running
        //noinspection ResultOfMethodCallIgnored
        TestHelper.getInstance();
        addTestSchema();
    }


    @SuppressWarnings({ "SqlNoDataSourceInspection" })
    private static void addTestSchema() throws SQLException {
        try ( JdbcConnection jdbcConnection = new JdbcConnection( false ) ) {
            Connection connection = jdbcConnection.getConnection();
            try ( Statement statement = connection.createStatement() ) {
                statement.executeUpdate( "CREATE SCHEMA test" );
                statement.executeUpdate( "CREATE TABLE test.resttest( "
                        + "tbigint BIGINT NOT NULL, "
                        + "tboolean BOOLEAN NOT NULL, "
                        + "tdate DATE NOT NULL, "
                        + "tdecimal DECIMAL(5,2) NOT NULL, "
                        + "tdouble DOUBLE NOT NULL, "
                        + "tinteger INTEGER NOT NULL, "
                        + "treal REAL NOT NULL, "
                        + "tsmallint SMALLINT NOT NULL, "
                        + "ttime TIME NOT NULL, "
                        + "ttimestamp TIMESTAMP NOT NULL, "
                        + "ttinyint TINYINT NOT NULL, "
                        + "tvarchar VARCHAR(20) NOT NULL, "
                        + "PRIMARY KEY (tinteger) )" );
                connection.commit();
            }
        }
    }


    private HttpResponse<String> executeRest( HttpRequest<?> request ) {
        request.basicAuth( "pa", "" );
        request.routeParam( "protocol", "http" );
        request.routeParam( "host", "127.0.0.1" );
        request.routeParam( "port", "8089" );
        log.debug( request.getUrl() );
        try {
            HttpResponse<String> result = request.asString();
            if ( !result.isSuccess() ) {
                throw new RuntimeException( "Error while executing REST query. Message: " + result.getStatusText() + "  |  URL: " + request.getUrl() );
            }
            return result;
        } catch ( UnirestException e ) {
            throw new RuntimeException( e );
        }
    }


    private static HttpRequest<?> buildRestInsert( String table, List<JsonObject> rows ) {
        JsonArray array = new JsonArray();
        rows.forEach( array::add );
        JsonObject data = new JsonObject();
        data.add( "data", array );

        return Unirest.post( "{protocol}://{host}:{port}/restapi/v1/res/" + table )
                .header( "Content-Type", "application/json" )
                .body( data );
    }


    private static HttpRequest<?> buildRestUpdate( String table, JsonObject set, Map<String, String> where ) {
        JsonArray array = new JsonArray();
        array.add( set );
        JsonObject data = new JsonObject();
        data.add( "data", array );

        RequestBodyEntity request = Unirest.patch( "{protocol}://{host}:{port}/restapi/v1/res/" + table )
                .header( "Content-Type", "application/json" )
                .body( data );

        for ( Map.Entry<String, String> entry : where.entrySet() ) {
            request.queryString( entry.getKey(), entry.getValue() );
        }

        return request;
    }


    private static HttpRequest<?> buildRestDelete( String table, Map<String, String> where ) {
        HttpRequest<?> request = Unirest.delete( "{protocol}://{host}:{port}/restapi/v1/res/" + table )
                .header( "Content-Type", "application/json" );

        for ( Map.Entry<String, String> entry : where.entrySet() ) {
            request.queryString( entry.getKey(), entry.getValue() );
        }

        return request;
    }

    // --------------- Tests ---------------


    @Test
    public void testOperations() {
        // Insert
        HttpRequest<?> request = buildRestInsert( "test.resttest", ImmutableList.of( getTestRow() ) );
        Assert.assertEquals(
                "{\"result\":[{\"ROWCOUNT\":1}],\"size\":1}",
                executeRest( request ).getBody() );

        // Update
        Map<String, String> where = new LinkedHashMap<>();
        where.put( "test.resttest.tsmallint", "=" + 45 );
        request = buildRestUpdate( "test.resttest", getTestRow(), where );
        Assert.assertEquals(
                "{\"result\":[{\"ROWCOUNT\":1}],\"size\":1}",
                executeRest( request ).getBody() );

        // Get
        request = Unirest.get( "{protocol}://{host}:{port}/restapi/v1/res/test.resttest" )
                .queryString( "test.resttest.ttinyint", "=" + 22 );
        Assert.assertEquals(
                "{\"result\":[{\"test.resttest.tsmallint\":45,\"test.resttest.tdecimal\":123.00,\"test.resttest.ttinyint\":22,\"test.resttest.treal\":0.3333,\"test.resttest.tinteger\":9876,\"test.resttest.ttime\":\"43505000\",\"test.resttest.tbigint\":1234,\"test.resttest.tboolean\":true,\"test.resttest.tdate\":18466,\"test.resttest.tdouble\":1.999999,\"test.resttest.tvarchar\":\"hallo\",\"test.resttest.ttimestamp\":\"2020-07-23T12:05:05\"}],\"size\":1,\"query\":\"test.resttest.ttinyint\\u003d%3D22\",\"uri\":\"/restapi/v1/res/test.resttest\"}",
                executeRest( request ).getBody() );

        // Delete
        where = new LinkedHashMap<>();
        where.put( "test.resttest.tvarchar", "=" + "hallo" );
        request = buildRestDelete( "test.resttest", where );
        Assert.assertEquals(
                "{\"result\":[{\"ROWCOUNT\":1}],\"size\":1}",
                executeRest( request ).getBody() );

        // Select
        request = Unirest.get( "{protocol}://{host}:{port}/restapi/v1/res/test.resttest" )
                .queryString( "test.resttest.tinteger", "=" + 9876 );
        Assert.assertEquals(
                "{\"result\":[],\"size\":0,\"query\":\"test.resttest.tinteger\\u003d%3D9876\",\"uri\":\"/restapi/v1/res/test.resttest\"}",
                executeRest( request ).getBody() );
    }


    private JsonObject getTestRow() {
        JsonObject row = new JsonObject();
        row.add(
                "test.resttest.tbigint",
                new JsonPrimitive( 1234L ) );
        row.add(
                "test.resttest.tboolean",
                new JsonPrimitive( true ) );
        row.add(
                "test.resttest.tdate",
                new JsonPrimitive( LocalDate.of( 2020, 7, 23 ).format( DateTimeFormatter.ISO_LOCAL_DATE ) ) );
        row.add(
                "test.resttest.tdecimal",
                new JsonPrimitive( new BigDecimal( "123.45" ) ) );
        row.add(
                "test.resttest.tdouble",
                new JsonPrimitive( 1.999999 ) );
        row.add(
                "test.resttest.tinteger",
                new JsonPrimitive( 9876 ) );
        row.add(
                "test.resttest.treal",
                new JsonPrimitive( 0.3333 ) );
        row.add(
                "test.resttest.tsmallint",
                new JsonPrimitive( 45 ) );
        row.add(
                "test.resttest.ttime",
                new JsonPrimitive( LocalTime.of( 12, 5, 5 ).format( DateTimeFormatter.ISO_LOCAL_TIME ) ) );
        row.add(
                "test.resttest.ttimestamp",
                new JsonPrimitive( LocalDateTime.of( 2020, 7, 23, 12, 5, 5 ).format( DateTimeFormatter.ISO_LOCAL_DATE_TIME ) ) );
        row.add(
                "test.resttest.ttinyint",
                new JsonPrimitive( 22 ) );
        row.add(
                "test.resttest.tvarchar",
                new JsonPrimitive( "hallo" ) );
        return row;
    }

}
