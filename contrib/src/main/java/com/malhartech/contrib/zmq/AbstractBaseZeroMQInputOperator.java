/*
 *  Copyright (c) 2012 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.malhartech.contrib.zmq;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;

import com.malhartech.api.ActivationListener;
import com.malhartech.api.InputOperator;
import com.malhartech.api.BaseOperator;
import com.malhartech.api.Context.OperatorContext;
import com.malhartech.util.CircularBuffer;

/**
 * ZeroMQ input adapter operator, which consume data from ZeroMQ message bus.<p><br>
 *
 * <br>
 * Ports:<br>
 * <b>Input</b>: No input port<br>
 * <b>Output</b>: Can have any number of output ports<br>
 * <br>
 * Properties:<br>
 * <b>tuple_blast</b>: Number of tuples emitted in each burst<br>
 * <b>bufferSize</b>: Size of holding buffer<br>
 * <b>url</b>:the url for the subscriber to connect to ZeroMQ publisher<br>
 * <b>syncUrl</b>: the url for the subscriber to synchronize with publisher<br>
 * <b>filter</b>: the filter that subscriber wants to subscribe, default is ""<br>
 * <br>
 * Compile time checks:<br>
 * Class derived from this has to implement the abstract method emitTuple() <br>
 * <br>
 * Run time checks:<br>
 * None<br>
 * <br>
 * <b>Benchmarks</b>: Blast as many tuples as possible in inline mode<br>
 * <table border="1" cellspacing=1 cellpadding=1 summary="Benchmark table for AbstractBaseZeroMQInputOperator&lt;K,V extends Number&gt; operator template">
 * <tr><th>In-Bound</th><th>Out-bound</th><th>Comments</th></tr>
 * <tr><td><b>400 thousand K,V pairs/s</td><td>One tuple per key per window per port</td><td>In-bound rate is the main determinant of performance. Operator can emit about 400 thousand unique (k,v immutable pairs) tuples/sec as ZeroMQ DAG. Tuples are assumed to be
 * immutable. If you use mutable tuples and have lots of keys, the benchmarks may differ</td></tr>
 * </table><br>
 * <br>
 * @author Zhongjian Wang <zhongjian@malhar-inc.com>
 */
public abstract class AbstractBaseZeroMQInputOperator extends BaseOperator implements InputOperator, ActivationListener<OperatorContext>
{
  private static final Logger logger = LoggerFactory.getLogger(AbstractBaseZeroMQInputOperator.class);
  transient protected ZMQ.Context context;
  transient protected ZMQ.Socket subscriber;
  transient protected ZMQ.Socket syncclient;
  private String url;
  @InjectConfig(key = "syncUrl")
  private String syncUrl;
  private String filter="";

  private static final int DEFAULT_BLAST_SIZE = 1000;
  private static final int DEFAULT_BUFFER_SIZE = 1024*1024;
  private int tuple_blast = DEFAULT_BLAST_SIZE;
  private int bufferSize = DEFAULT_BUFFER_SIZE;
  private volatile boolean running = false;
  transient CircularBuffer<byte[]> holdingBuffer = new CircularBuffer<byte[]>(bufferSize);

  public void setUrl(String url)
  {
    this.url = url;
  }

  public void setSyncUrl(String url)
  {
    this.syncUrl = url;
  }

  public void setFilter(String filter)
  {
    this.filter = filter;
  }

//  @Min(1)
  public void setTupleBlast(int i)
  {
    this.tuple_blast = i;
  }
  public void setBufferSize(int size) {
    this.bufferSize = size;
  }
  @Override
  public void setup(OperatorContext ctx)
  {
    context = ZMQ.context(1);
    subscriber = context.socket(ZMQ.SUB);
    subscriber.connect(url);
    subscriber.subscribe(filter.getBytes());
    syncclient = context.socket(ZMQ.REQ);
    syncclient.connect(syncUrl);
    syncclient.send("".getBytes(), 0);
  }

  @Override
  public void teardown()
  {
    subscriber.close();
    syncclient.close();
    context.term();
  }

  // The other thread
  public void activate(OperatorContext ctx)
  {
    new Thread()
    {
      @Override
      public void run()
      {
        running = true;
        while (running) {
          try {
            byte[] message = subscriber.recv(0);
            if (message != null) {
              holdingBuffer.add(message);
            }
          }
          catch (Exception e) {
//        logger.debug(e.toString());
            break;
          }
        }
      }
    }.start();
  }

  public void deactivate()
  {
    running = false;
  }

  public abstract void emitTuple(byte[] message);

  @Override
  public void emitTuples()
  {
    int ntuples = tuple_blast;
    if (ntuples > holdingBuffer.size()) {
      ntuples = holdingBuffer.size();
    }
    for (int i = ntuples; i-- > 0;) {
      emitTuple(holdingBuffer.pollUnsafe());
    }
  }
}