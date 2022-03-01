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

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.catalog.Catalog.QueryLanguage;
import org.polypheny.db.languages.ParserPos;
import org.polypheny.db.nodes.Node;
import org.polypheny.db.nodes.NodeVisitor;
import org.polypheny.db.util.Litmus;

public abstract class CypherNode implements Node {

    @Getter
    public final ParserPos pos;

    public static final List<CypherKind> DDL = ImmutableList.of( CypherKind.CREATE, CypherKind.DROP );


    protected CypherNode( ParserPos pos ) {
        this.pos = pos;
    }


    @Override
    public Kind getKind() {
        return Kind.OTHER;
    }


    public abstract CypherKind getCypherKind();


    @Override
    public Node clone( ParserPos pos ) {
        return null;
    }


    @Override
    public QueryLanguage getLanguage() {
        return QueryLanguage.CYPHER;
    }


    @Override
    public boolean isA( Set<Kind> category ) {
        return false;
    }


    @Override
    public boolean equalsDeep( Node node, Litmus litmus ) {
        return false;
    }


    @Override
    public <R> R accept( NodeVisitor<R> visitor ) {
        return null;
    }


    public boolean isDDL() {
        return DDL.contains( getCypherKind() );
    }


    public enum CypherKind {
        SCOPE, REMOVE, ADMIN_COMMAND, QUERY, PATTERN, EXPRESSION, WITH_GRAPH, CALL, CASE, CREATE, SCHEMA_COMMAND, ADMIN_ACTION, DELETE, DROP, FOR_EACH, LOAD_CSV, MATCH, MERGE, ORDER_ITEM, RETURN, SET, SHOW, TRANSACTION, UNWIND, USE, WAIT, WHERE, WITH, MAP_PROJECTION, YIELD, EITHER, RESOURCE, PRIVILEGE, PATH_LENGTH, CALL_RESULT, HINT, PATH, SET_ITEM
    }

}
