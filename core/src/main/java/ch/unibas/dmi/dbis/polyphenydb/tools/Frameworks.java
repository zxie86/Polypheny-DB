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

package ch.unibas.dmi.dbis.polyphenydb.tools;


import ch.unibas.dmi.dbis.polyphenydb.DataContext;
import ch.unibas.dmi.dbis.polyphenydb.DataContext.SlimDataContext;
import ch.unibas.dmi.dbis.polyphenydb.adapter.java.JavaTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.config.PolyphenyDbConnectionProperty;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.ContextImpl;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.JavaTypeFactoryImpl;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbSchema;
import ch.unibas.dmi.dbis.polyphenydb.materialize.MapSqlStatisticProvider;
import ch.unibas.dmi.dbis.polyphenydb.materialize.SqlStatisticProvider;
import ch.unibas.dmi.dbis.polyphenydb.plan.Context;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCluster;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCostFactory;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptSchema;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptTable.ViewExpander;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelTraitDef;
import ch.unibas.dmi.dbis.polyphenydb.prepare.PlannerImpl;
import ch.unibas.dmi.dbis.polyphenydb.prepare.PolyphenyDbPrepareImpl;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeSystem;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexExecutor;
import ch.unibas.dmi.dbis.polyphenydb.schema.SchemaPlus;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlOperatorTable;
import ch.unibas.dmi.dbis.polyphenydb.sql.fun.SqlStdOperatorTable;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParser;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParser.Config;
import ch.unibas.dmi.dbis.polyphenydb.sql2rel.SqlRexConvertletTable;
import ch.unibas.dmi.dbis.polyphenydb.sql2rel.SqlToRelConverter;
import ch.unibas.dmi.dbis.polyphenydb.sql2rel.StandardConvertletTable;
import ch.unibas.dmi.dbis.polyphenydb.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;


/**
 * Tools for invoking Polypheny-DB functionality without initializing a container / server first.
 */
public class Frameworks {

    private Frameworks() {
    }


    /**
     * Creates a planner.
     *
     * @param config Planner configuration
     * @return Planner
     */
    public static Planner getPlanner( FrameworkConfig config ) {
        return new PlannerImpl( config );
    }


    /**
     * Piece of code to be run in a context where a planner is available. The planner is accessible from the {@code cluster} parameter, as are several other useful objects.
     *
     * @param <R> result type
     */
    public interface PlannerAction<R> {

        R apply( RelOptCluster cluster, RelOptSchema relOptSchema, SchemaPlus rootSchema );
    }


    /**
     * Piece of code to be run in a context where a planner and statement are available. The planner is accessible from the {@code cluster} parameter, as are several other useful objects. The connection and
     * {@link DataContext} are accessible from the statement.
     *
     * @param <R> result type
     */
    public abstract static class PrepareAction<R> {

        private final FrameworkConfig config;


        public PrepareAction( FrameworkConfig config ) {
            this.config = config;
        }


        public FrameworkConfig getConfig() {
            return config;
        }


        public abstract R apply(
                RelOptCluster cluster,
                RelOptSchema relOptSchema,
                SchemaPlus rootSchema );
    }


    /**
     * Initializes a container then calls user-specified code with a planner.
     *
     * @param action Callback containing user-specified code
     * @param config FrameworkConfig to use for planner action.
     * @return Return value from action
     */
    public static <R> R withPlanner( final PlannerAction<R> action, final FrameworkConfig config ) {
        return withPrepare(
                new Frameworks.PrepareAction<R>( config ) {
                    public R apply( RelOptCluster cluster, RelOptSchema relOptSchema, SchemaPlus rootSchema ) {
                        final PolyphenyDbSchema schema = PolyphenyDbSchema.from( Util.first( config.getDefaultSchema(), rootSchema ) );
                        return action.apply( cluster, relOptSchema, schema.root().plus() );
                    }
                } );
    }


    /**
     * Initializes a container then calls user-specified code with a planner.
     *
     * @param action Callback containing user-specified code
     * @return Return value from action
     */
    public static <R> R withPlanner( final PlannerAction<R> action ) {
        SchemaPlus rootSchema = Frameworks.createRootSchema( true );
        FrameworkConfig config = newConfigBuilder()
                .defaultSchema( rootSchema )
                .prepareContext( new ContextImpl( PolyphenyDbSchema.from( rootSchema ), new SlimDataContext() {
                    @Override
                    public JavaTypeFactory getTypeFactory() {
                        return new JavaTypeFactoryImpl();
                    }
                }, "" ) )
                .build();
        return withPlanner( action, config );
    }


    /**
     * Initializes a container then calls user-specified code with a planner and statement.
     *
     * @param action Callback containing user-specified code
     * @return Return value from action
     */
    public static <R> R withPrepare( PrepareAction<R> action ) {
        try {
            final Properties info = new Properties();
            if ( action.config.getTypeSystem() != RelDataTypeSystem.DEFAULT ) {
                info.setProperty(
                        PolyphenyDbConnectionProperty.TYPE_SYSTEM.camelName(),
                        action.config.getTypeSystem().getClass().getName() );
            }

            return new PolyphenyDbPrepareImpl().perform( action );
        } catch ( Exception e ) {
            throw new RuntimeException( e );
        }
    }


    /**
     * Creates a root schema.
     *
     * @param cache Whether to create a caching schema.
     */
    public static SchemaPlus createRootSchema( boolean cache ) {
        return PolyphenyDbSchema.createRootSchema( cache ).plus();
    }


    /**
     * Creates a config builder with each setting initialized to its default value.
     */
    public static ConfigBuilder newConfigBuilder() {
        return new ConfigBuilder();
    }


    /**
     * Creates a config builder initializing each setting from an existing config.
     *
     * So, {@code newConfigBuilder(config).build()} will return a value equal to {@code config}.
     */
    public static ConfigBuilder newConfigBuilder( FrameworkConfig config ) {
        return new ConfigBuilder( config );
    }


    /**
     * A builder to help you build a {@link FrameworkConfig} using defaults where values aren't required.
     */
    public static class ConfigBuilder {

        private SqlRexConvertletTable convertletTable;
        private SqlOperatorTable operatorTable;
        private ImmutableList<Program> programs;
        private Context context;
        private ImmutableList<RelTraitDef> traitDefs;
        private Config parserConfig;
        private SqlToRelConverter.Config sqlToRelConverterConfig;
        private SchemaPlus defaultSchema;
        private RexExecutor executor;
        private RelOptCostFactory costFactory;
        private RelDataTypeSystem typeSystem;
        private boolean evolveLattice;
        private SqlStatisticProvider statisticProvider;
        private ViewExpander viewExpander;
        private ch.unibas.dmi.dbis.polyphenydb.jdbc.Context prepareContext;


        /**
         * Creates a ConfigBuilder, initializing to defaults.
         */
        private ConfigBuilder() {
            convertletTable = StandardConvertletTable.INSTANCE;
            operatorTable = SqlStdOperatorTable.instance();
            programs = ImmutableList.of();
            parserConfig = SqlParser.Config.DEFAULT;
            sqlToRelConverterConfig = SqlToRelConverter.Config.DEFAULT;
            typeSystem = RelDataTypeSystem.DEFAULT;
            evolveLattice = false;
            statisticProvider = MapSqlStatisticProvider.INSTANCE;
        }


        /**
         * Creates a ConfigBuilder, initializing from an existing config.
         */
        private ConfigBuilder( FrameworkConfig config ) {
            convertletTable = config.getConvertletTable();
            operatorTable = config.getOperatorTable();
            programs = config.getPrograms();
            context = config.getContext();
            traitDefs = config.getTraitDefs();
            parserConfig = config.getParserConfig();
            sqlToRelConverterConfig = config.getSqlToRelConverterConfig();
            defaultSchema = config.getDefaultSchema();
            executor = config.getExecutor();
            costFactory = config.getCostFactory();
            typeSystem = config.getTypeSystem();
            evolveLattice = config.isEvolveLattice();
            statisticProvider = config.getStatisticProvider();
            prepareContext = config.getPrepareContext();
        }


        public FrameworkConfig build() {
            return new StdFrameworkConfig(
                    context,
                    convertletTable,
                    operatorTable,
                    programs,
                    traitDefs,
                    parserConfig,
                    sqlToRelConverterConfig,
                    defaultSchema,
                    costFactory,
                    typeSystem,
                    executor,
                    evolveLattice,
                    statisticProvider,
                    viewExpander,
                    prepareContext );
        }


        public ConfigBuilder context( Context c ) {
            this.context = Objects.requireNonNull( c );
            return this;
        }


        public ConfigBuilder executor( RexExecutor executor ) {
            this.executor = Objects.requireNonNull( executor );
            return this;
        }


        public ConfigBuilder convertletTable( SqlRexConvertletTable convertletTable ) {
            this.convertletTable = Objects.requireNonNull( convertletTable );
            return this;
        }


        public ConfigBuilder operatorTable( SqlOperatorTable operatorTable ) {
            this.operatorTable = Objects.requireNonNull( operatorTable );
            return this;
        }


        public ConfigBuilder traitDefs( List<RelTraitDef> traitDefs ) {
            if ( traitDefs == null ) {
                this.traitDefs = null;
            } else {
                this.traitDefs = ImmutableList.copyOf( traitDefs );
            }
            return this;
        }


        public ConfigBuilder traitDefs( RelTraitDef... traitDefs ) {
            this.traitDefs = ImmutableList.copyOf( traitDefs );
            return this;
        }


        public ConfigBuilder parserConfig( SqlParser.Config parserConfig ) {
            this.parserConfig = Objects.requireNonNull( parserConfig );
            return this;
        }


        public ConfigBuilder sqlToRelConverterConfig( SqlToRelConverter.Config sqlToRelConverterConfig ) {
            this.sqlToRelConverterConfig = Objects.requireNonNull( sqlToRelConverterConfig );
            return this;
        }


        public ConfigBuilder defaultSchema( SchemaPlus defaultSchema ) {
            this.defaultSchema = defaultSchema;
            return this;
        }


        public ConfigBuilder costFactory( RelOptCostFactory costFactory ) {
            this.costFactory = costFactory;
            return this;
        }


        public ConfigBuilder ruleSets( RuleSet... ruleSets ) {
            return programs( Programs.listOf( ruleSets ) );
        }


        public ConfigBuilder ruleSets( List<RuleSet> ruleSets ) {
            return programs( Programs.listOf( Objects.requireNonNull( ruleSets ) ) );
        }


        public ConfigBuilder programs( List<Program> programs ) {
            this.programs = ImmutableList.copyOf( programs );
            return this;
        }


        public ConfigBuilder programs( Program... programs ) {
            this.programs = ImmutableList.copyOf( programs );
            return this;
        }


        public ConfigBuilder typeSystem( RelDataTypeSystem typeSystem ) {
            this.typeSystem = Objects.requireNonNull( typeSystem );
            return this;
        }


        public ConfigBuilder evolveLattice( boolean evolveLattice ) {
            this.evolveLattice = evolveLattice;
            return this;
        }


        public ConfigBuilder statisticProvider( SqlStatisticProvider statisticProvider ) {
            this.statisticProvider = Objects.requireNonNull( statisticProvider );
            return this;
        }


        public ConfigBuilder viewExpander( ViewExpander viewExpander ) {
            this.viewExpander = viewExpander;
            return this;
        }


        public ConfigBuilder prepareContext( ch.unibas.dmi.dbis.polyphenydb.jdbc.Context prepareContext ) {
            this.prepareContext = prepareContext;
            return this;
        }
    }


    /**
     * An implementation of {@link FrameworkConfig} that uses standard Polypheny-DB classes to provide basic planner functionality.
     */
    static class StdFrameworkConfig implements FrameworkConfig {

        private final Context context;
        private final SqlRexConvertletTable convertletTable;
        private final SqlOperatorTable operatorTable;
        private final ImmutableList<Program> programs;
        private final ImmutableList<RelTraitDef> traitDefs;
        private final SqlParser.Config parserConfig;
        private final SqlToRelConverter.Config sqlToRelConverterConfig;
        private final SchemaPlus defaultSchema;
        private final RelOptCostFactory costFactory;
        private final RelDataTypeSystem typeSystem;
        private final RexExecutor executor;
        private final boolean evolveLattice;
        private final SqlStatisticProvider statisticProvider;
        private final ViewExpander viewExpander;
        private final ch.unibas.dmi.dbis.polyphenydb.jdbc.Context prepareContext;


        StdFrameworkConfig(
                Context context,
                SqlRexConvertletTable convertletTable,
                SqlOperatorTable operatorTable,
                ImmutableList<Program> programs,
                ImmutableList<RelTraitDef> traitDefs,
                SqlParser.Config parserConfig,
                SqlToRelConverter.Config sqlToRelConverterConfig,
                SchemaPlus defaultSchema,
                RelOptCostFactory costFactory,
                RelDataTypeSystem typeSystem,
                RexExecutor executor,
                boolean evolveLattice,
                SqlStatisticProvider statisticProvider,
                ViewExpander viewExpander,
                ch.unibas.dmi.dbis.polyphenydb.jdbc.Context prepareContext ) {
            this.context = context;
            this.convertletTable = convertletTable;
            this.operatorTable = operatorTable;
            this.programs = programs;
            this.traitDefs = traitDefs;
            this.parserConfig = parserConfig;
            this.sqlToRelConverterConfig = sqlToRelConverterConfig;
            this.defaultSchema = defaultSchema;
            this.costFactory = costFactory;
            this.typeSystem = typeSystem;
            this.executor = executor;
            this.evolveLattice = evolveLattice;
            this.statisticProvider = statisticProvider;
            this.viewExpander = viewExpander;
            this.prepareContext = prepareContext;
        }


        @Override
        public SqlParser.Config getParserConfig() {
            return parserConfig;
        }


        @Override
        public SqlToRelConverter.Config getSqlToRelConverterConfig() {
            return sqlToRelConverterConfig;
        }


        @Override
        public SchemaPlus getDefaultSchema() {
            return defaultSchema;
        }


        @Override
        public RexExecutor getExecutor() {
            return executor;
        }


        @Override
        public ImmutableList<Program> getPrograms() {
            return programs;
        }


        @Override
        public RelOptCostFactory getCostFactory() {
            return costFactory;
        }


        @Override
        public ImmutableList<RelTraitDef> getTraitDefs() {
            return traitDefs;
        }


        @Override
        public SqlRexConvertletTable getConvertletTable() {
            return convertletTable;
        }


        @Override
        public Context getContext() {
            return context;
        }


        @Override
        public SqlOperatorTable getOperatorTable() {
            return operatorTable;
        }


        @Override
        public RelDataTypeSystem getTypeSystem() {
            return typeSystem;
        }


        @Override
        public boolean isEvolveLattice() {
            return evolveLattice;
        }


        @Override
        public SqlStatisticProvider getStatisticProvider() {
            return statisticProvider;
        }


        @Override
        public ViewExpander getViewExpander() {
            return viewExpander;
        }


        @Override
        public ch.unibas.dmi.dbis.polyphenydb.jdbc.Context getPrepareContext() {
            return prepareContext;
        }
    }
}
