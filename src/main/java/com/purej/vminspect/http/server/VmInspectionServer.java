// Copyright (c), 2013, adopus consulting GmbH Switzerland, all rights reserved.
package com.purej.vminspect.http.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.purej.vminspect.data.statistics.StatisticsCollector;
import com.purej.vminspect.http.RequestController;

/**
 * This standalone server allows PureJ VM Inspection to be used without a servlet-container or other type of
 * web-server. It implements a very basic and lightweight HTTP server that handles only HTTP/1.0/1.1 GET requests
 * used by the VM Inspection functionality.
 * <p/>
 * Note: This class starts some listener threads and opens a server-socket so it should NEVER be used inside JEE
 * application server containers!
 *
 * @author Stefan Mueller
 */
public final class VmInspectionServer {
  private static final Logger LOGGER = LoggerFactory.getLogger(VmInspectionServer.class);
  private final ExecutorService _executor;
  private final ServerSocket _serverSocket;
  private final Thread _listener;
  private final StatisticsCollector _collector;
  private final RequestController _controller;

  /**
   * Creates a new instance of this very basic HTTP server.
   * @param port the port where the server-socket listens for incoming HTTP requests
   * @throws IOException if the server socket could not be bound to the given port
   */
  public VmInspectionServer(int port) throws IOException {
    this(false, 60000, null, port);
  }

  /**
   * Creates a new instance of this very basic HTTP server.
   *
   * @param mbeansReadonly if MBeans should be accessed read-only
   * @param statisticsCollectionFrequencyMs the statistics collection frequency in milliseconds (60'000 recommended)
   * @param statisticsStorageDir the optional statistics storage directory
   * @param port the port where the server-socket listens for incoming HTTP requests
   * @throws IOException if the server socket could not be bound to the given port
   */
  public VmInspectionServer(boolean mbeansReadonly, int statisticsCollectionFrequencyMs, String statisticsStorageDir, int port) throws IOException {
    // Create the executor to handle request:
    _executor = Executors.newFixedThreadPool(3, new ThreadFactory() {
      @Override
      public Thread newThread(Runnable target) {
        return new Thread(target, "PureJ VM Inspection HTTP request executor");
      }
    });

    // Open the server-socket:
    _serverSocket = new ServerSocket(port, 10);

    // Create the listener thread:
    _listener = new Thread("PureJ VM Inspection HTTP listener") {
      @Override
      public void run() {
        while (!_serverSocket.isClosed()) {
          try {
            Socket socket = _serverSocket.accept();
            _executor.execute(new RequestExecutor(socket, _controller));
          }
          catch (Exception e) {
            if (_serverSocket.isClosed()) {
              break;
            }
            LOGGER.error("An error occurred accepting incomming HTTP connection!", e);
          }
        }
      }
    };
    _listener.setDaemon(true);

    // Get or create collector, create controller and startup:
    _collector = StatisticsCollector.init(statisticsStorageDir, statisticsCollectionFrequencyMs, this);
    _controller = new RequestController(_collector, mbeansReadonly);
    _listener.start();
  }

  /**
   * Initiates an orderly shutdown in which previously accepted HTTP request are executed but no new incoming
   * requests will be accepted. The {@link StatisticsCollector} will be stopped as well if this instance was
   * the last reference to it.
   */
  public void shutdown() {
    StatisticsCollector.destroy(this);
    try {
      _serverSocket.close();
    }
    catch (Exception e) {
      // Ignored...
    }
    _executor.shutdown();
  }
}