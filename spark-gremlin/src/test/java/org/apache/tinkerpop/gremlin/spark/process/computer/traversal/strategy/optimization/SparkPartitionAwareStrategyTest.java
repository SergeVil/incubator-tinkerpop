/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.tinkerpop.gremlin.spark.process.computer.traversal.strategy.optimization;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.TestHelper;
import org.apache.tinkerpop.gremlin.hadoop.Constants;
import org.apache.tinkerpop.gremlin.hadoop.structure.io.gryo.GryoInputFormat;
import org.apache.tinkerpop.gremlin.process.computer.traversal.step.map.TraversalVertexProgramStep;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.spark.AbstractSparkTest;
import org.apache.tinkerpop.gremlin.spark.process.computer.SparkGraphComputer;
import org.apache.tinkerpop.gremlin.spark.process.computer.SparkHadoopGraphProvider;
import org.apache.tinkerpop.gremlin.spark.structure.io.PersistedOutputRDD;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SparkPartitionAwareStrategyTest extends AbstractSparkTest {

    @Test
    public void shouldSuccessfullyEvaluateSkipPartitionedTraversals() throws Exception {
        final String outputLocation = TestHelper.makeTestDataDirectory(SparkPartitionAwareStrategyTest.class, UUID.randomUUID().toString());
        Configuration configuration = getBaseConfiguration();
        configuration.setProperty(Constants.GREMLIN_HADOOP_INPUT_LOCATION, SparkHadoopGraphProvider.PATHS.get("tinkerpop-modern.kryo"));
        configuration.setProperty(Constants.GREMLIN_HADOOP_GRAPH_READER, GryoInputFormat.class.getCanonicalName());
        configuration.setProperty(Constants.GREMLIN_HADOOP_GRAPH_WRITER, PersistedOutputRDD.class.getCanonicalName());
        configuration.setProperty(Constants.GREMLIN_HADOOP_OUTPUT_LOCATION, outputLocation);
        configuration.setProperty(Constants.GREMLIN_HADOOP_JARS_IN_DISTRIBUTED_CACHE, false);
        configuration.setProperty(Constants.GREMLIN_HADOOP_DEFAULT_GRAPH_COMPUTER, SparkGraphComputer.class.getCanonicalName());
        ///
        Graph graph = GraphFactory.open(configuration);
        GraphTraversalSource g = graph.traversal().withComputer();
        assertTrue(g.getStrategies().toList().contains(SparkPartitionAwareStrategy.instance()));
        assertTrue(g.V().count().explain().toString().contains(SparkPartitionAwareStrategy.class.getSimpleName()));
        //
        assertEquals(6l, g.V().count().next().longValue());
        assertEquals(2l, g.V().out().out().count().next().longValue());
    }


    @Test
    public void shouldSkipPartitionExceptedTraversals() throws Exception {
        final String outputLocation = TestHelper.makeTestDataDirectory(SparkPartitionAwareStrategyTest.class, UUID.randomUUID().toString());
        Configuration configuration = getBaseConfiguration();
        configuration.setProperty(Constants.GREMLIN_HADOOP_INPUT_LOCATION, SparkHadoopGraphProvider.PATHS.get("tinkerpop-modern.kryo"));
        configuration.setProperty(Constants.GREMLIN_HADOOP_GRAPH_READER, GryoInputFormat.class.getCanonicalName());
        configuration.setProperty(Constants.GREMLIN_HADOOP_GRAPH_WRITER, PersistedOutputRDD.class.getCanonicalName());
        configuration.setProperty(Constants.GREMLIN_HADOOP_OUTPUT_LOCATION, outputLocation);
        configuration.setProperty(Constants.GREMLIN_HADOOP_DEFAULT_GRAPH_COMPUTER, SparkGraphComputer.class.getCanonicalName());

        Graph graph = GraphFactory.open(configuration);
        GraphTraversalSource g = graph.traversal().withComputer();

        assertTrue(skipPartitioner(g.V().limit(10)));
        assertTrue(skipPartitioner(g.V().values("age").groupCount()));
        assertTrue(skipPartitioner(g.V().groupCount().by(__.out().count())));
        assertTrue(skipPartitioner(g.V().outE()));
        assertTrue(skipPartitioner(g.V().count()));
        assertTrue(skipPartitioner(g.V().out().count()));
        assertTrue(skipPartitioner(g.V().local(__.inE()).count()));
        assertTrue(skipPartitioner(g.V().outE().inV().count()));
        ////
        assertFalse(skipPartitioner(g.V().outE().inV()));
        assertFalse(skipPartitioner(g.V().both()));
        assertFalse(skipPartitioner(g.V().both().count()));
        assertFalse(skipPartitioner(g.V().out().id()));
        assertFalse(skipPartitioner(g.V().out().out().count()));
        assertFalse(skipPartitioner(g.V().in().count()));
        assertFalse(skipPartitioner(g.V().inE().count()));


    }

    private static boolean skipPartitioner(final Traversal<?, ?> traversal) {
        traversal.asAdmin().applyStrategies();
        return (Boolean) TraversalHelper.getFirstStepOfAssignableClass(TraversalVertexProgramStep.class, traversal.asAdmin()).get()
                .getComputer()
                .getConfiguration()
                .getOrDefault(Constants.GREMLIN_SPARK_SKIP_PARTITIONER, false);

    }

}