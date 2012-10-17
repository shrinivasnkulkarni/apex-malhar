/**
 * Copyright (c) 2012-2012 Malhar, Inc. All rights reserved.
 */
package com.malhartech.lib.testbench;

import com.malhartech.dag.Component;
import com.malhartech.dag.ModuleConfiguration;
import com.malhartech.dag.ModuleContext;
import com.malhartech.dag.Sink;
import com.malhartech.dag.Tuple;
import com.malhartech.stram.ManualScheduledExecutorService;
import com.malhartech.stram.WindowGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import junit.framework.Assert;
import org.apache.hadoop.conf.Configuration;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Functional tests for {@link com.malhartech.lib.testbench.EventGenerator}. <p>
 * <br>
 * Load is generated and the tuples are outputted to ensure that the numbers are roughly in line with the weights<br>
 * <br>
 *  Benchmarks:<br>
 * String schema generates over 11 Million tuples/sec<br>
 * HashMap schema generates over 1.7 Million tuples/sec<br>
 * <br>
 * DRC checks are validated<br>
 *
 */
public class EventGeneratorTest {

    private static Logger LOG = LoggerFactory.getLogger(EventGenerator.class);

    class TestSink implements Sink {

        HashMap<String, Integer> collectedTuples = new HashMap<String, Integer>();

        //DefaultSerDe serde = new DefaultSerDe();
        int count = 0;
        boolean test_hashmap = false;
        boolean skiphash = true;

        /**
         *
         * @param payload
         */
        @Override
        public void process(Object payload) {
            if (payload instanceof Tuple) {
                // LOG.debug(payload.toString());
            }
            else {
              if (!skiphash) {
                if (test_hashmap) {
                    HashMap<String, Double> tuple = (HashMap<String, Double>) payload;
                    for (Map.Entry<String, Double> e : tuple.entrySet()) {
                        String str = e.getKey();
                        Integer val = collectedTuples.get(str);
                        if (val != null) {
                            val = val + 1;
                        } else {
                            val = new Integer(1);
                        }
                        collectedTuples.put(str, val);
                    }
                }
                else {
                    String str = (String) payload;
                    Integer val = collectedTuples.get(str);
                    if (val != null) {
                        val = val + 1;
                    } else {
                        val = new Integer(1);
                    }
                    collectedTuples.put(str, val);
                }
              }
              count++;
                //serde.toByteArray(payload);
             }
        }
    }

    class TestCountSink implements Sink {

        //DefaultSerDe serde = new DefaultSerDe();
        int count = 0;
        int average = 0;
        int num_tuples = 0;

        /**
         *
         * @param payload
         */
        @Override
        public void process(Object payload) {
            if (payload instanceof Tuple) {
                // LOG.debug(payload.toString());
            }
            else {
              HashMap<String, Integer> tuples = (HashMap<String, Integer>) payload;
              average = tuples.get(EventGenerator.OPORT_COUNT_TUPLE_AVERAGE).intValue();
              count += tuples.get(EventGenerator.OPORT_COUNT_TUPLE_COUNT).intValue();
              num_tuples++;
                //serde.toByteArray(payload);
             }
        }
    }

    /**
     * Test configuration and parameter validation of the node
     */
    @Test
    public void testNodeValidation() {

        ModuleConfiguration conf = new ModuleConfiguration("mynode", new HashMap<String, String>());
        EventGenerator node = new EventGenerator();

        conf.set(EventGenerator.KEY_KEYS, "");
        try {
            node.myValidation(conf);
            Assert.fail("validation error  " + EventGenerator.KEY_KEYS);
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("validate " + EventGenerator.KEY_KEYS,
                    e.getMessage().contains("is empty"));
        }

        conf.set(EventGenerator.KEY_KEYS, "a,b,c,d"); // from now on keys would be a,b,c,d
        conf.set(EventGenerator.KEY_WEIGHTS, "10.4,40,20,30");
        try {
            node.myValidation(conf);
            Assert.fail("validation error  " + EventGenerator.KEY_WEIGHTS);
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("validate " + EventGenerator.KEY_WEIGHTS,
                    e.getMessage().contains("should be an integer"));
        }

        conf.set(EventGenerator.KEY_WEIGHTS, "10,40,20"); // from now on weights would be 10,40,20,30
        try {
            node.myValidation(conf);
            Assert.fail("validation error  " + EventGenerator.KEY_WEIGHTS);
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("validate " + EventGenerator.KEY_WEIGHTS,
                    e.getMessage().contains("does not match number of keys"));
        }

        conf.set(EventGenerator.KEY_WEIGHTS, ""); // from now on weights would be 10,40,20,30
        conf.set(EventGenerator.KEY_VALUES, "a,2,3,4");
        try {
            node.myValidation(conf);
            Assert.fail("validation error  " + EventGenerator.KEY_VALUES);
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("validate " + EventGenerator.KEY_VALUES,
                    e.getMessage().contains("should be float"));
        }

        conf.set(EventGenerator.KEY_WEIGHTS, "10,40,30,20"); // from now on weights would be 10,40,20,30
        conf.set(EventGenerator.KEY_VALUES, "1,2,3");
        try {
            node.myValidation(conf);
            Assert.fail("validation error  " + EventGenerator.KEY_VALUES);
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("validate " + EventGenerator.KEY_VALUES,
                    e.getMessage().contains("does not match number of keys"));
        }

        conf.set(EventGenerator.KEY_VALUES, "1,2,3,4");
        conf.set(EventGenerator.KEY_TUPLES_BLAST, "-1");
        try {
            node.myValidation(conf);
            Assert.fail("validation error  " + EventGenerator.KEY_TUPLES_BLAST);
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("validate " + EventGenerator.KEY_TUPLES_BLAST,
                    e.getMessage().contains("has to be > 0"));
        }

        conf.set(EventGenerator.KEY_VALUES, "1,2,3,4");
        conf.set(EventGenerator.KEY_STRING_SCHEMA, "true");
        conf.set(EventGenerator.KEY_TUPLES_BLAST, "10000");
        try {
            node.myValidation(conf);
            Assert.fail("validation error  " + EventGenerator.KEY_STRING_SCHEMA);
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("validate " + EventGenerator.KEY_STRING_SCHEMA + " and " + EventGenerator.KEY_VALUES,
                    e.getMessage().contains("if string_schema"));
        }

        conf.set(EventGenerator.KEY_VALUES, "");
        conf.set(EventGenerator.ROLLING_WINDOW_COUNT, "aa");
        String rstr = conf.get(EventGenerator.ROLLING_WINDOW_COUNT);
        try {
            node.myValidation(conf);
            Assert.fail("validation error  " + EventGenerator.ROLLING_WINDOW_COUNT);
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("validate " + EventGenerator.ROLLING_WINDOW_COUNT,
                    e.getMessage().contains("has to be an integer"));
        }
    }

  /**
   * Tests both string and non string schema
   */
  @Test
  public void testNodeProcessing() throws Exception
  {
    testSingleSchemaNodeProcessing(true, true); // 10.5 million/s
    testSingleSchemaNodeProcessing(true, false); // 7.5 million/s
    testSingleSchemaNodeProcessing(false, true); // 4.7 million/s
    testSingleSchemaNodeProcessing(false, false); // 3 million/s
  }

    /**
     * Test node logic emits correct results
     */
  @SuppressWarnings("SleepWhileInLoop")
    public void testSingleSchemaNodeProcessing(boolean stringschema, boolean skiphash) throws Exception {

        final EventGenerator node = new EventGenerator();
        final ManualScheduledExecutorService mses = new ManualScheduledExecutorService(1);
        final WindowGenerator wingen = new WindowGenerator(mses);

        Configuration config = new Configuration();
        config.setLong(WindowGenerator.FIRST_WINDOW_MILLIS, 0);
        config.setInt(WindowGenerator.WINDOW_WIDTH_MILLIS, 1);
        wingen.setup(config);

        Sink input = node.connect(Component.INPUT, wingen);
        wingen.connect("mytestnode", input);

        TestCountSink countSink = new TestCountSink();
        TestSink lgenSink = new TestSink();
        node.connect(EventGenerator.OPORT_DATA, lgenSink);
        node.connect(EventGenerator.OPORT_COUNT, countSink);
        ModuleConfiguration conf = new ModuleConfiguration("mynode", new HashMap<String, String>());

        conf.set(EventGenerator.KEY_KEYS, "a,b,c,d");
        // conf.set(EventGenerator.KEY_VALUES, "1,2,3,4");
        conf.set(EventGenerator.KEY_VALUES, "");
      if (stringschema) {
        conf.set(EventGenerator.KEY_STRING_SCHEMA, "true");
      }
      else {
        conf.set(EventGenerator.KEY_STRING_SCHEMA, "false");
      }
      lgenSink.test_hashmap = !stringschema;
      lgenSink.skiphash = skiphash;
      conf.set(EventGenerator.KEY_WEIGHTS, "10,40,20,30");
      conf.setInt(EventGenerator.KEY_TUPLES_BLAST, 10000000);
      conf.setInt(EventGenerator.ROLLING_WINDOW_COUNT, 5);
      conf.setInt("SpinMillis", 10);
      conf.setInt("BufferCapacity", 1024 * 1024);

        node.setup(conf);

        final AtomicBoolean inactive = new AtomicBoolean(true);
        new Thread() {
            @Override
            public void run() {
                inactive.set(false);
                node.activate(new ModuleContext("LoadGeneratorTestNode", this));
            }
        }.start();

        /**
         * spin while the node gets activated.
         */
        try {
            do {
                Thread.sleep(20);
            } while (inactive.get());
        } catch (InterruptedException ex) {
            LOG.debug(ex.getLocalizedMessage());
        }
        wingen.activate(null);
        for (int i = 0; i < 500; i++) {
            mses.tick(1);
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                LOG.error("Unexpected error while sleeping for 1 s", e);
            }
        }
        node.deactivate();

        // Let the reciever get the tuples from the queue
        for (int i = 0; i < 15; i++) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                LOG.error("Unexpected error while sleeping for 1 s", e);
            }
        }


        // Assert.assertEquals("number emitted tuples", 5000, lgenSink.collectedTuples.size());
//        LOG.debug("Processed {} tuples out of {}", lgenSink.collectedTuples.size(), lgenSink.count);
        LOG.debug(String.format("\n********************************************\nTesting with %s and%s insertion\nLoadGenerator emitted %d (%d) tuples in %d windows; Sink processed %d tuples",
                  stringschema ? "String" : "HashMap", skiphash ? " no" : "",
                  countSink.count, countSink.average, countSink.num_tuples, lgenSink.count));

        for (Map.Entry<String, Integer> e: lgenSink.collectedTuples.entrySet()) {
            LOG.debug("{} tuples for key {}", e.getValue().intValue(), e.getKey());
        }

    }
}
