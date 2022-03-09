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

package org.polypheny.db.cypher.expression;

import lombok.Getter;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.cypher.cypher2alg.CypherToAlgConverter.CypherContext;
import org.polypheny.db.languages.ParserPos;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.util.Pair;

@Getter
public class CypherVariable extends CypherExpression {

    private final String name;


    public CypherVariable( ParserPos pos, String name ) {
        super( pos );
        this.name = name;
    }


    @Override
    public Pair<String, RexNode> getRexAsProject( CypherContext context ) {
        AlgNode node = context.peek();

        int index = node.getRowType().getFieldNames().indexOf( name );

        if ( index < 0 ) {
            throw new RuntimeException( "The used variable is not known." );
        }

        return Pair.of(
                name,
                context.rexBuilder.makeInputRef( node.getRowType().getFieldList().get( index ).getType(), index ) );

    }

}