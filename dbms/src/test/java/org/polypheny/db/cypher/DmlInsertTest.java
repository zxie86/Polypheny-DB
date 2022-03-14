/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.cypher;

import java.util.List;
import org.junit.Test;
import org.polypheny.db.TestHelper.CypherConnection;
import org.polypheny.db.cypher.helper.TestEdge;
import org.polypheny.db.cypher.helper.TestNode;
import org.polypheny.db.util.Pair;
import org.polypheny.db.webui.models.Result;

public class DmlInsertTest extends CypherTestTemplate {

    @Test
    public void selectTest() {
        Result res = execute( "MATCH (n)\nRETURN n" );
    }


    @Test
    public void insertNodeTest() {
        execute( "CREATE (p:Person {name: 'Max Muster'})" );
        Result res = execute( "MATCH (n)\nRETURN n" );
        assert containsNodes( res, true,
                TestNode.from( Pair.of( "name", "Max Muster" ) ) );
    }


    @Test
    public void insertPropertyTypeTest() {
        execute( "CREATE (p:Person {name: 'Max Muster', age: 13, height: 185.3, nicknames: [\"Maxi\",\"Musti\"]})" );
        Result res = execute( "MATCH (n)\nRETURN n" );
        assert containsNodes( res, true,
                TestNode.from(
                        Pair.of( "name", "Max Muster" ),
                        Pair.of( "age", 13 ),
                        Pair.of( "height", 185.3 ),
                        Pair.of( "nicknames", List.of( "Maxi", "Musti" ) )
                ) );
    }


    @Test
    public void insertReturnNodeTest() {
        Result res = execute(
                "CREATE (p:Person {name: 'Max Muster'})\n"
                        + "RETURN p" );
    }


    @Test
    public void insertSingleHopPathTest() {
        execute( "CREATE (p:Person {name: 'Max'})-[rel:OWNER_OF]->(a:Animal {name:'Kira', age:3, type:'dog'})" );
        Result res = execute( "MATCH (n) RETURN n" );
        assert containsNodes( res, true,
                TestNode.from( List.of( "Person" ), Pair.of( "name", "Max" ) ),
                TestNode.from(
                        List.of( "Animal" ),
                        Pair.of( "name", "Kira" ),
                        Pair.of( "age", 3 ),
                        Pair.of( "type", "dog" ) ) );
        assert containsEdges( res, true, TestEdge.from( List.of( "OWNER_OF" ) ) );
    }


    @Test
    public void insertMultipleHopPathTest() {
        execute( "CREATE (n:Person)-[f:FRIEND_OF]->(p:Person {name: 'Max'})-[rel:OWNER_OF]->(a:Animal {name:'Kira'})" );
        Result res = execute( "MATCH (n) RETURN n" );
        assert containsNodes( res, true,
                TestNode.from( List.of( "Person" ) ),
                TestNode.from( List.of( "Person" ), Pair.of( "name", "Max" ) ),
                TestNode.from( List.of( "Animal" ), Pair.of( "name", "Kira" ) ) );
        assert containsEdges( res, true,
                TestEdge.from( List.of( "OWNER_OF" ) ),
                TestEdge.from( List.of( "FRIEND_OF" ) ) );
    }


    @Test
    public void insertAdditionalRelationshipTest() {
        Result createPerson = CypherConnection.executeGetResponse(
                "CREATE (p:Person {name: 'Max'})" );

        Result createAnimal = CypherConnection.executeGetResponse(
                "CREATE (a:ANIMAL {name:'Kira', age:3, type:'dog'})" );

        Result createRel = CypherConnection.executeGetResponse(
                "MATCH (max:Person {name: 'Max'})\n"
                        + "MATCH (kira:ANIMAL {name: 'Kira'})\n"
                        + "CREATE (max)-[rel:OWNER_OF]->(kira)" );
    }

}
