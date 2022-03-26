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

package org.polypheny.db.routing.routers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.GraphAlg;
import org.polypheny.db.algebra.GraphAlg.NodeType;
import org.polypheny.db.algebra.core.BatchIterator;
import org.polypheny.db.algebra.core.ConditionalExecute;
import org.polypheny.db.algebra.core.SetOp;
import org.polypheny.db.algebra.core.Union;
import org.polypheny.db.algebra.logical.LogicalModify;
import org.polypheny.db.algebra.logical.LogicalScan;
import org.polypheny.db.algebra.logical.LogicalValues;
import org.polypheny.db.catalog.entity.CatalogEntity;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.prepare.AlgOptTableImpl;
import org.polypheny.db.routing.LogicalQueryInformation;
import org.polypheny.db.routing.Router;
import org.polypheny.db.schema.LogicalTable;
import org.polypheny.db.tools.AlgBuilder;
import org.polypheny.db.tools.RoutedAlgBuilder;
import org.polypheny.db.transaction.Statement;


/**
 * Abstract router for DQL routing.
 * It will create alg builders and select column placements per table.
 * There are three abstract methods:
 * handleHorizontalPartitioning
 * handleVerticalPartitioning
 * handleNonePartitioning
 * Every table will be check by the following order:
 * first if partitioned horizontally {@code ->} handleHorizontalPartitioning called.
 * second if partitioned vertically or replicated {@code ->} handleVerticalPartitioningOrReplication
 * third, all other cases {@code ->} handleNonePartitioning
 */
@Slf4j
public abstract class AbstractDqlRouter extends BaseRouter implements Router {

    /**
     * Boolean which cancel routing plan generation.
     *
     * In Universal Routing, not every router needs to propose a plan for every query. But, every router will be asked to try
     * to prepare a plan for the query. If a router sees, that he is not able to route (maybe after tablePlacement 2 out of 4,
     * the router is expected to return an empty list of proposed routing plans. To "abort" routing plan creation, the boolean
     * cancelQuery is introduced and will be checked during creation. As we are in the middle of traversing a AlgNode, this
     * is the new to "abort" traversing the AlgNode.
     */
    protected boolean cancelQuery = false;


    /**
     * Abstract methods which will determine routing strategy. Not implemented in abstract class.
     * If implementing router is not supporting one of the three methods, empty list can be returned and cancelQuery boolean set to false.
     */
    protected abstract List<RoutedAlgBuilder> handleHorizontalPartitioning(
            AlgNode node,
            CatalogEntity catalogEntity,
            Statement statement,
            LogicalTable logicalTable,
            List<RoutedAlgBuilder> builders,
            AlgOptCluster cluster,
            LogicalQueryInformation queryInformation );

    protected abstract List<RoutedAlgBuilder> handleVerticalPartitioningOrReplication(
            AlgNode node,
            CatalogEntity catalogEntity,
            Statement statement,
            LogicalTable logicalTable,
            List<RoutedAlgBuilder> builders,
            AlgOptCluster cluster,
            LogicalQueryInformation queryInformation );

    protected abstract List<RoutedAlgBuilder> handleNonePartitioning(
            AlgNode node,
            CatalogEntity catalogEntity,
            Statement statement,
            List<RoutedAlgBuilder> builders,
            AlgOptCluster cluster,
            LogicalQueryInformation queryInformation );


    /**
     * Abstract router only routes DQL queries.
     */
    @Override
    public List<RoutedAlgBuilder> route( AlgRoot logicalRoot, Statement statement, LogicalQueryInformation queryInformation ) {
        // Reset cancel query this run
        this.cancelQuery = false;

        if ( logicalRoot.alg instanceof LogicalModify ) {
            throw new IllegalStateException( "Should never happen for DML" );
        } else if ( logicalRoot.alg instanceof ConditionalExecute ) {
            throw new IllegalStateException( "Should never happen for conditional executes" );
        } else if ( logicalRoot.alg instanceof BatchIterator ) {
            throw new IllegalStateException( "Should never happen for Iterator" );
        } else {
            RoutedAlgBuilder builder = RoutedAlgBuilder.create( statement, logicalRoot.alg.getCluster() );
            List<RoutedAlgBuilder> routedAlgBuilders = buildDql(
                    logicalRoot.alg,
                    Lists.newArrayList( builder ),
                    statement,
                    logicalRoot.alg.getCluster(),
                    queryInformation );
            return routedAlgBuilders;
        }
    }


    @Override
    public <T extends AlgNode & GraphAlg> AlgNode routeGraph( T alg, Statement statement ) {
        if ( alg.getInputs().size() == 1 ) {
            routeGraph( (AlgNode & GraphAlg) alg.getInput( 0 ), statement );
            return alg;
        } else if ( alg.getNodeType() == NodeType.SCAN ) {
            attachMappingsIfNecessary( alg );
            return alg;
        } else if ( alg.getNodeType() == NodeType.VALUES ) {
            return alg;
        }
        throw new UnsupportedOperationException();
    }


    @Override
    public void resetCaches() {
        joinedScanCache.invalidateAll();
    }


    protected List<RoutedAlgBuilder> buildDql( AlgNode node, List<RoutedAlgBuilder> builders, Statement statement, AlgOptCluster cluster, LogicalQueryInformation queryInformation ) {
        if ( node instanceof SetOp ) {
            if ( node instanceof Union ) {
                // unions can have more than one child
                return buildUnion( node, builders, statement, cluster, queryInformation );
            } else {
                return buildSetOp( node, builders, statement, cluster, queryInformation );
            }
        } else {
            return buildSelect( node, builders, statement, cluster, queryInformation );
        }
    }


    protected List<RoutedAlgBuilder> buildSelect( AlgNode node, List<RoutedAlgBuilder> builders, Statement statement, AlgOptCluster cluster, LogicalQueryInformation queryInformation ) {
        if ( cancelQuery ) {
            return Collections.emptyList();
        }

        for ( int i = 0; i < node.getInputs().size(); i++ ) {
            builders = this.buildDql( node.getInput( i ), builders, statement, cluster, queryInformation );
        }

        if ( node instanceof LogicalScan && node.getTable() != null ) {
            AlgOptTableImpl table = (AlgOptTableImpl) node.getTable();

            if ( !(table.getTable() instanceof LogicalTable) ) {
                throw new RuntimeException( "Unexpected table. Only logical tables expected here!" );
            }

            LogicalTable logicalTable = ((LogicalTable) table.getTable());
            CatalogEntity catalogEntity = catalog.getTable( logicalTable.getTableId() );

            // Check if table is even horizontal partitioned
            if ( catalogEntity.partitionProperty.isPartitioned ) {
                return handleHorizontalPartitioning( node, catalogEntity, statement, logicalTable, builders, cluster, queryInformation );

            } else {
                // At the moment multiple strategies
                if ( catalogEntity.dataPlacements.size() > 1 ) {
                    return handleVerticalPartitioningOrReplication( node, catalogEntity, statement, logicalTable, builders, cluster, queryInformation );
                }
                return handleNonePartitioning( node, catalogEntity, statement, builders, cluster, queryInformation );
            }

        } else if ( node instanceof LogicalValues ) {
            return Lists.newArrayList( super.handleValues( (LogicalValues) node, builders ) );
        } else {
            return Lists.newArrayList( super.handleGeneric( node, builders ) );
        }
    }


    protected List<RoutedAlgBuilder> buildSetOp( AlgNode node, List<RoutedAlgBuilder> builders, Statement statement, AlgOptCluster cluster, LogicalQueryInformation queryInformation ) {
        if ( cancelQuery ) {
            return Collections.emptyList();
        }
        builders = buildDql( node.getInput( 0 ), builders, statement, cluster, queryInformation );

        RoutedAlgBuilder builder0 = RoutedAlgBuilder.create( statement, cluster );
        RoutedAlgBuilder b0 = buildDql( node.getInput( 1 ), Lists.newArrayList( builder0 ), statement, cluster, queryInformation ).get( 0 );

        builders.forEach(
                builder -> builder.replaceTop( node.copy( node.getTraitSet(), ImmutableList.of( builder.peek(), b0.build() ) ) )
        );

        return builders;
    }


    protected List<RoutedAlgBuilder> buildUnion( AlgNode node, List<RoutedAlgBuilder> builders, Statement statement, AlgOptCluster cluster, LogicalQueryInformation queryInformation ) {
        if ( cancelQuery ) {
            return Collections.emptyList();
        }
        builders = buildDql( node.getInput( 0 ), builders, statement, cluster, queryInformation );

        RoutedAlgBuilder builder0 = RoutedAlgBuilder.create( statement, cluster );
        List<RoutedAlgBuilder> b0s = new ArrayList<>();
        for ( AlgNode input : node.getInputs().subList( 1, node.getInputs().size() ) ) {
            b0s.add( buildDql( input, Lists.newArrayList( builder0 ), statement, cluster, queryInformation ).get( 0 ) );
        }

        builders.forEach(
                builder -> builder.replaceTop( node.copy(
                        node.getTraitSet(),
                        ImmutableList.copyOf(
                                Stream.concat(
                                                Stream.of( builder.peek() ),
                                                b0s.stream().map( AlgBuilder::build ) )
                                        .collect( Collectors.toList() ) ) ) )
        );

        return builders;
    }

}
