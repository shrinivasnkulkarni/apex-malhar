package com.datatorrent.lib.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import junit.framework.Assert;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.lib.testbench.CollectorTestSink;

/**
 * Functional test for {@link com.datatorrent.lib.io.AbstractSocketInputOperator}.
 */
public class SocketInputOperatorTest
{
  private static String testData = "src/test/resources/SocketInputOperatorTest.txt";
  private StringBuffer strBuffer = new StringBuffer();

  public class TestSocketInputOperator extends AbstractSocketInputOperator<String>
  {
    @Override
    public void processBytes(ByteBuffer byteBuffer)
    {
      final byte[] bytes = new byte[byteBuffer.remaining()];
      byteBuffer.duplicate().get(bytes);
      outputPort.emit(new String(bytes));
    }
  }

  public class Server implements Runnable
  {
    private int serverPort;

    Server(int port)
    {
      this.serverPort = port;
    }

    @Override
    public void run()
    {
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(testData)));
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        SocketAddress port = new InetSocketAddress(serverPort);
        serverChannel.socket().bind(port);
        while (true) {
          SocketChannel clientChannel = serverChannel.accept();
          String line = reader.readLine();
          ByteBuffer buffer;
          byte[] data;
          while (line != null) {
            strBuffer.append(line);
            data = line.getBytes();
            buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
              clientChannel.write(buffer);
            }
            line = reader.readLine();
          }
          reader.close();
          clientChannel.close();
        }
      }
      catch (Exception e) {
        //LOG.debug("server ", e);
      }
    }
  }

  @Test
  public void Test()
  {
    try {
      Thread server = new Thread(new Server(7898));
      server.start();
      Thread.sleep(10000);
      // server.join();
      TestSocketInputOperator operator = new TestSocketInputOperator();
      operator.setHostname("localhost");
      operator.setPort(7898);
      operator.setScanIntervalInMilliSeconds(10);
      CollectorTestSink sink = new CollectorTestSink();
      operator.outputPort.setSink(sink);
      operator.setup(null);
      operator.activate(null);
      operator.beginWindow(0);
      Thread.sleep(1000);
      operator.emitTuples();
      Thread.sleep(1000);
      operator.emitTuples();
      operator.endWindow();
      operator.deactivate();
      operator.teardown();
      String outputString = (String) sink.collectedTuples.get(0);
      Assert.assertEquals(strBuffer.substring(0, outputString.length()), sink.collectedTuples.get(0));
      int length = outputString.length();
      outputString = (String) sink.collectedTuples.get(1);
      Assert.assertEquals(strBuffer.substring(length, length + outputString.length()), sink.collectedTuples.get(1));
      server.interrupt();
      server.join();
      Thread.sleep(10000);
    }
    catch (Exception e) {
      LOG.debug("exception", e);
    }
  }

  @Test
  public void TestWithSmallerBufferSize()
  {
    try {
      Thread server = new Thread(new Server(7899));
      server.start();
      Thread.sleep(10000);
      TestSocketInputOperator operator = new TestSocketInputOperator();
      operator.setHostname("localhost");
      operator.setPort(7899);
      operator.setScanIntervalInMilliSeconds(10);
      operator.setByteBufferSize(10);
      CollectorTestSink sink = new CollectorTestSink();
      operator.outputPort.setSink(sink);
      operator.setup(null);
      operator.activate(null);
      operator.beginWindow(0);
      Thread.sleep(1000);
      for (int i = 0; i < 10; i++) {
        operator.emitTuples();
        Thread.sleep(1000);
      }
      operator.endWindow();
      operator.deactivate();
      operator.teardown();
      Assert.assertEquals(10, sink.collectedTuples.size());
      for (int i = 0; i < 10; i++) {
        Assert.assertEquals(strBuffer.substring(i * 10, (i + 1) * 10), sink.collectedTuples.get(i));
      }
      server.interrupt();
      server.join();
      Thread.sleep(10000);
    }
    catch (Exception e) {
      LOG.debug("exception", e);
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(SocketInputOperatorTest.class);
}