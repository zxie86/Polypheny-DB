/*
 * This file is based on code taken from the Apache Calcite project, which was released under the Apache License.
 * The changes are released under the MIT license.
 *
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.rel.rules;


import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptRule;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptRuleCall;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptRuleOperand;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Join;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.RelFactories;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.SetOp;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Union;
import ch.unibas.dmi.dbis.polyphenydb.tools.RelBuilderFactory;
import java.util.ArrayList;
import java.util.List;


/**
 * Planner rule that pushes a
 * {@link ch.unibas.dmi.dbis.polyphenydb.rel.core.Join}
 * past a non-distinct {@link ch.unibas.dmi.dbis.polyphenydb.rel.core.Union}.
 */
public class JoinUnionTransposeRule extends RelOptRule {

    public static final JoinUnionTransposeRule LEFT_UNION =
            new JoinUnionTransposeRule(
                    operand( Join.class, operand( Union.class, any() ), operand( RelNode.class, any() ) ),
                    RelFactories.LOGICAL_BUILDER,
                    "JoinUnionTransposeRule(Union-Other)" );

    public static final JoinUnionTransposeRule RIGHT_UNION =
            new JoinUnionTransposeRule(
                    operand( Join.class, operand( RelNode.class, any() ), operand( Union.class, any() ) ),
                    RelFactories.LOGICAL_BUILDER,
                    "JoinUnionTransposeRule(Other-Union)" );


    /**
     * Creates a JoinUnionTransposeRule.
     *
     * @param operand root operand, must not be null
     * @param description Description, or null to guess description
     * @param relBuilderFactory Builder for relational expressions
     */
    public JoinUnionTransposeRule( RelOptRuleOperand operand, RelBuilderFactory relBuilderFactory, String description ) {
        super( operand, relBuilderFactory, description );
    }


    public void onMatch( RelOptRuleCall call ) {
        final Join join = call.rel( 0 );
        final Union unionRel;
        RelNode otherInput;
        boolean unionOnLeft;
        if ( call.rel( 1 ) instanceof Union ) {
            unionRel = call.rel( 1 );
            otherInput = call.rel( 2 );
            unionOnLeft = true;
        } else {
            otherInput = call.rel( 1 );
            unionRel = call.rel( 2 );
            unionOnLeft = false;
        }
        if ( !unionRel.all ) {
            return;
        }
        if ( !join.getVariablesSet().isEmpty() ) {
            return;
        }
        // The UNION ALL cannot be on the null generating side of an outer join (otherwise we might generate incorrect rows for the other side for join keys which lack a match in one or both branches of the union)
        if ( unionOnLeft ) {
            if ( join.getJoinType().generatesNullsOnLeft() ) {
                return;
            }
        } else {
            if ( join.getJoinType().generatesNullsOnRight() ) {
                return;
            }
        }
        List<RelNode> newUnionInputs = new ArrayList<>();
        for ( RelNode input : unionRel.getInputs() ) {
            RelNode joinLeft;
            RelNode joinRight;
            if ( unionOnLeft ) {
                joinLeft = input;
                joinRight = otherInput;
            } else {
                joinLeft = otherInput;
                joinRight = input;
            }
            newUnionInputs.add(
                    join.copy(
                            join.getTraitSet(),
                            join.getCondition(),
                            joinLeft,
                            joinRight,
                            join.getJoinType(),
                            join.isSemiJoinDone() ) );
        }
        final SetOp newUnionRel = unionRel.copy( unionRel.getTraitSet(), newUnionInputs, true );
        call.transformTo( newUnionRel );
    }
}
