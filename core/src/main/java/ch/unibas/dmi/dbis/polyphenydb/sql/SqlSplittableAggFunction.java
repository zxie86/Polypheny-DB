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

package ch.unibas.dmi.dbis.polyphenydb.sql;


import ch.unibas.dmi.dbis.polyphenydb.rel.RelCollations;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.AggregateCall;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeField;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexBuilder;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexInputRef;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexUtil;
import ch.unibas.dmi.dbis.polyphenydb.sql.fun.SqlStdOperatorTable;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeName;
import ch.unibas.dmi.dbis.polyphenydb.util.ImmutableIntList;
import ch.unibas.dmi.dbis.polyphenydb.util.mapping.Mappings.TargetMapping;
import com.google.common.collect.ImmutableList;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * Aggregate function that can be split into partial aggregates.
 *
 * For example, {@code COUNT(x)} can be split into {@code COUNT(x)} on subsets followed by {@code SUM} to combine those counts.
 */
public interface SqlSplittableAggFunction {

    AggregateCall split( AggregateCall aggregateCall, TargetMapping mapping );

    /**
     * Called to generate an aggregate for the other side of the join than the side aggregate call's arguments come from. Returns null if no aggregate is required.
     */
    AggregateCall other( RelDataTypeFactory typeFactory, AggregateCall e );

    /**
     * Generates an aggregate call to merge sub-totals.
     *
     * Most implementations will add a single aggregate call to {@code aggCalls}, and return a {@link RexInputRef} that points to it.
     *
     * @param rexBuilder Rex builder
     * @param extra Place to define extra input expressions
     * @param offset Offset due to grouping columns (and indicator columns if applicable)
     * @param inputRowType Input row type
     * @param aggregateCall Source aggregate call
     * @param leftSubTotal Ordinal of the sub-total coming from the left side of the join, or -1 if there is no such sub-total
     * @param rightSubTotal Ordinal of the sub-total coming from the right side of the join, or -1 if there is no such sub-total
     * @return Aggregate call
     */
    AggregateCall topSplit( RexBuilder rexBuilder, Registry<RexNode> extra, int offset, RelDataType inputRowType, AggregateCall aggregateCall, int leftSubTotal, int rightSubTotal );

    /**
     * Generates an expression for the value of the aggregate function when applied to a single row.
     *
     * For example, if there is one row:
     * <ul>
     * <li>{@code SUM(x)} is {@code x}</li>
     * <li>{@code MIN(x)} is {@code x}</li>
     * <li>{@code MAX(x)} is {@code x}</li>
     * <li>{@code COUNT(x)} is {@code CASE WHEN x IS NOT NULL THEN 1 ELSE 0 END 1} which can be simplified to {@code 1} if {@code x} is never null</li>
     * <li>{@code COUNT(*)} is 1</li>
     * </ul>
     *
     * @param rexBuilder Rex builder
     * @param inputRowType Input row type
     * @param aggregateCall Aggregate call
     * @return Expression for single row
     */
    RexNode singleton( RexBuilder rexBuilder, RelDataType inputRowType, AggregateCall aggregateCall );

    /**
     * Collection in which one can register an element. Registering may return a reference to an existing element.
     *
     * @param <E> element type
     */
    interface Registry<E> {

        int register( E e );
    }


    /**
     * Splitting strategy for {@code COUNT}.
     *
     * COUNT splits into itself followed by SUM. (Actually SUM0, because the total needs to be 0, not null, if there are 0 rows.)
     * This rule works for any number of arguments to COUNT, including COUNT(*).
     */
    class CountSplitter implements SqlSplittableAggFunction {

        public static final CountSplitter INSTANCE = new CountSplitter();


        public AggregateCall split( AggregateCall aggregateCall, TargetMapping mapping ) {
            return aggregateCall.transform( mapping );
        }


        public AggregateCall other( RelDataTypeFactory typeFactory, AggregateCall e ) {
            return AggregateCall.create(
                    SqlStdOperatorTable.COUNT,
                    false,
                    false,
                    ImmutableIntList.of(),
                    -1,
                    RelCollations.EMPTY,
                    typeFactory.createSqlType( SqlTypeName.BIGINT ),
                    null );
        }


        public AggregateCall topSplit( RexBuilder rexBuilder, Registry<RexNode> extra, int offset, RelDataType inputRowType, AggregateCall aggregateCall, int leftSubTotal, int rightSubTotal ) {
            final List<RexNode> merges = new ArrayList<>();
            if ( leftSubTotal >= 0 ) {
                merges.add( rexBuilder.makeInputRef( aggregateCall.type, leftSubTotal ) );
            }
            if ( rightSubTotal >= 0 ) {
                merges.add( rexBuilder.makeInputRef( aggregateCall.type, rightSubTotal ) );
            }
            RexNode node;
            switch ( merges.size() ) {
                case 1:
                    node = merges.get( 0 );
                    break;
                case 2:
                    node = rexBuilder.makeCall( SqlStdOperatorTable.MULTIPLY, merges );
                    break;
                default:
                    throw new AssertionError( "unexpected count " + merges );
            }
            int ordinal = extra.register( node );
            return AggregateCall.create(
                    SqlStdOperatorTable.SUM0,
                    false,
                    false,
                    ImmutableList.of( ordinal ),
                    -1,
                    aggregateCall.collation,
                    aggregateCall.type,
                    aggregateCall.name );
        }


        /**
         * {@inheritDoc}
         *
         * {@code COUNT(*)}, and {@code COUNT} applied to all NOT NULL arguments, become {@code 1}; otherwise {@code CASE WHEN arg0 IS NOT NULL THEN 1 ELSE 0 END}.
         */
        public RexNode singleton( RexBuilder rexBuilder, RelDataType inputRowType, AggregateCall aggregateCall ) {
            final List<RexNode> predicates = new ArrayList<>();
            for ( Integer arg : aggregateCall.getArgList() ) {
                final RelDataType type = inputRowType.getFieldList().get( arg ).getType();
                if ( type.isNullable() ) {
                    predicates.add( rexBuilder.makeCall( SqlStdOperatorTable.IS_NOT_NULL, rexBuilder.makeInputRef( type, arg ) ) );
                }
            }
            final RexNode predicate = RexUtil.composeConjunction( rexBuilder, predicates, true );
            if ( predicate == null ) {
                return rexBuilder.makeExactLiteral( BigDecimal.ONE );
            } else {
                return rexBuilder.makeCall(
                        SqlStdOperatorTable.CASE,
                        predicate,
                        rexBuilder.makeExactLiteral( BigDecimal.ONE ),
                        rexBuilder.makeExactLiteral( BigDecimal.ZERO ) );
            }
        }
    }


    /**
     * Aggregate function that splits into two applications of itself.
     *
     * Examples are MIN and MAX.
     */
    class SelfSplitter implements SqlSplittableAggFunction {

        public static final SelfSplitter INSTANCE = new SelfSplitter();


        public RexNode singleton( RexBuilder rexBuilder, RelDataType inputRowType, AggregateCall aggregateCall ) {
            final int arg = aggregateCall.getArgList().get( 0 );
            final RelDataTypeField field = inputRowType.getFieldList().get( arg );
            return rexBuilder.makeInputRef( field.getType(), arg );
        }


        public AggregateCall split( AggregateCall aggregateCall, TargetMapping mapping ) {
            return aggregateCall.transform( mapping );
        }


        public AggregateCall other( RelDataTypeFactory typeFactory, AggregateCall e ) {
            return null; // no aggregate function required on other side
        }


        public AggregateCall topSplit( RexBuilder rexBuilder, Registry<RexNode> extra, int offset, RelDataType inputRowType, AggregateCall aggregateCall, int leftSubTotal, int rightSubTotal ) {
            assert (leftSubTotal >= 0) != (rightSubTotal >= 0);
            assert aggregateCall.collation.getFieldCollations().isEmpty();
            final int arg = leftSubTotal >= 0 ? leftSubTotal : rightSubTotal;
            return aggregateCall.copy( ImmutableIntList.of( arg ), -1, RelCollations.EMPTY );
        }
    }


    /**
     * Common splitting strategy for {@code SUM} and {@code SUM0} functions.
     */
    abstract class AbstractSumSplitter implements SqlSplittableAggFunction {

        public RexNode singleton( RexBuilder rexBuilder, RelDataType inputRowType, AggregateCall aggregateCall ) {
            final int arg = aggregateCall.getArgList().get( 0 );
            final RelDataTypeField field = inputRowType.getFieldList().get( arg );
            return rexBuilder.makeInputRef( field.getType(), arg );
        }


        public AggregateCall split( AggregateCall aggregateCall, TargetMapping mapping ) {
            return aggregateCall.transform( mapping );
        }


        public AggregateCall other( RelDataTypeFactory typeFactory, AggregateCall e ) {
            return AggregateCall.create(
                    SqlStdOperatorTable.COUNT,
                    false,
                    false,
                    ImmutableIntList.of(),
                    -1,
                    RelCollations.EMPTY,
                    typeFactory.createSqlType( SqlTypeName.BIGINT ),
                    null );
        }


        public AggregateCall topSplit( RexBuilder rexBuilder, Registry<RexNode> extra, int offset, RelDataType inputRowType, AggregateCall aggregateCall, int leftSubTotal, int rightSubTotal ) {
            final List<RexNode> merges = new ArrayList<>();
            final List<RelDataTypeField> fieldList = inputRowType.getFieldList();
            if ( leftSubTotal >= 0 ) {
                final RelDataType type = fieldList.get( leftSubTotal ).getType();
                merges.add( rexBuilder.makeInputRef( type, leftSubTotal ) );
            }
            if ( rightSubTotal >= 0 ) {
                final RelDataType type = fieldList.get( rightSubTotal ).getType();
                merges.add( rexBuilder.makeInputRef( type, rightSubTotal ) );
            }
            RexNode node;
            switch ( merges.size() ) {
                case 1:
                    node = merges.get( 0 );
                    break;
                case 2:
                    node = rexBuilder.makeCall( SqlStdOperatorTable.MULTIPLY, merges );
                    node = rexBuilder.makeAbstractCast( aggregateCall.type, node );
                    break;
                default:
                    throw new AssertionError( "unexpected count " + merges );
            }
            int ordinal = extra.register( node );
            return AggregateCall.create(
                    getMergeAggFunctionOfTopSplit(),
                    false,
                    false,
                    ImmutableList.of( ordinal ),
                    -1,
                    aggregateCall.collation,
                    aggregateCall.type,
                    aggregateCall.name );
        }


        protected abstract SqlAggFunction getMergeAggFunctionOfTopSplit();

    }


    /**
     * Splitting strategy for {@code SUM} function.
     */
    class SumSplitter extends AbstractSumSplitter {

        public static final SumSplitter INSTANCE = new SumSplitter();


        @Override
        public SqlAggFunction getMergeAggFunctionOfTopSplit() {
            return SqlStdOperatorTable.SUM;
        }
    }


    /**
     * Splitting strategy for {@code SUM0} function.
     */
    class Sum0Splitter extends AbstractSumSplitter {

        public static final Sum0Splitter INSTANCE = new Sum0Splitter();


        @Override
        public SqlAggFunction getMergeAggFunctionOfTopSplit() {
            return SqlStdOperatorTable.SUM0;
        }
    }
}
