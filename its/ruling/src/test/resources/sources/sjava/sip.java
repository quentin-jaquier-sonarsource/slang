/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.servlet.sip.core;

import gov.nist.javax.sip.ClientTransactionExt;
import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.DialogTimeoutEvent.Reason;
import gov.nist.javax.sip.IOExceptionEventExt;
import gov.nist.javax.sip.ResponseEventExt;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.TransactionExt;
import gov.nist.javax.sip.message.MessageExt;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.spi.ServiceRegistry;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.servlet.sip.SipErrorEvent;
import javax.servlet.sip.SipErrorListener;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.ar.SipApplicationRouter;
import javax.servlet.sip.ar.SipApplicationRouterInfo;
import javax.servlet.sip.ar.SipApplicationRoutingRegion;
import javax.servlet.sip.ar.SipRouteModifier;
import javax.servlet.sip.ar.spi.SipApplicationRouterProvider;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.Transaction;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.Header;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.Parameters;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.mobicents.ext.javax.sip.dns.DNSServerLocator;
import org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingListener;
import org.mobicents.ha.javax.sip.SipLoadBalancer;
import org.mobicents.javax.servlet.CongestionControlEvent;
import org.mobicents.javax.servlet.CongestionControlPolicy;
import org.mobicents.javax.servlet.ContainerListener;
import org.mobicents.javax.servlet.sip.dns.DNSResolver;
import org.mobicents.servlet.sip.GenericUtils;
import org.mobicents.servlet.sip.JainSipUtils;
import org.mobicents.servlet.sip.SipConnector;
import org.mobicents.servlet.sip.address.AddressImpl;
import org.mobicents.servlet.sip.address.AddressImpl.ModifiableRule;
import org.mobicents.servlet.sip.annotation.ConcurrencyControlMode;
import org.mobicents.servlet.sip.core.b2bua.MobicentsB2BUAHelper;
import org.mobicents.servlet.sip.core.dispatchers.MessageDispatcher;
import org.mobicents.servlet.sip.core.dispatchers.MessageDispatcherFactory;
import org.mobicents.servlet.sip.core.message.MobicentsSipServletRequest;
import org.mobicents.servlet.sip.core.proxy.MobicentsProxy;
import org.mobicents.servlet.sip.core.session.DistributableSipManager;
import org.mobicents.servlet.sip.core.session.MobicentsSipApplicationSession;
import org.mobicents.servlet.sip.core.session.MobicentsSipSession;
import org.mobicents.servlet.sip.core.session.MobicentsSipSessionKey;
import org.mobicents.servlet.sip.core.session.SessionManagerUtil;
import org.mobicents.servlet.sip.core.session.SipApplicationSessionKey;
import org.mobicents.servlet.sip.dns.MobicentsDNSResolver;
import org.mobicents.servlet.sip.listener.SipConnectorListener;
import org.mobicents.servlet.sip.message.SipFactoryImpl;
import org.mobicents.servlet.sip.message.SipServletMessageImpl;
import org.mobicents.servlet.sip.message.SipServletRequestImpl;
import org.mobicents.servlet.sip.message.SipServletResponseImpl;
import org.mobicents.servlet.sip.message.TransactionApplicationData;
import org.mobicents.servlet.sip.proxy.ProxyBranchImpl;
import org.mobicents.servlet.sip.proxy.ProxyImpl;
import org.mobicents.servlet.sip.router.ManageableApplicationRouter;
import org.mobicents.servlet.sip.utils.NamingThreadFactory;
import org.restcomm.commons.statistics.reporter.RestcommStatsReporter;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.mobicents.javax.servlet.CongestionStartedEvent;
import org.mobicents.javax.servlet.CongestionStoppedEvent;
import org.mobicents.javax.servlet.ContainerEvent;
import org.mobicents.javax.servlet.RequestThrottledEvent;


/**
 * Implementation of the SipApplicationDispatcher interface.
 * Central point getting the sip messages from the different stacks for a Tomcat Service(Engine),
 * translating jain sip SIP messages to sip servlets SIP messages, creating a MessageRouter responsible
 * for choosing which Dispatcher will be used for routing the message and
 * dispatches the messages.
 * @author Jean Deruelle
 */
public class SipApplicationDispatcherImpl implements SipApplicationDispatcher, SipApplicationDispatcherImplMBean, MBeanRegistration, LoadBalancerHeartBeatingListener {

  //list of methods supported by the AR
  private static final String[] METHODS_SUPPORTED =
      {"REGISTER", "INVITE", "ACK", "BYE", "CANCEL", "MESSAGE", "INFO", "SUBSCRIBE", "NOTIFY", "UPDATE", "PUBLISH", "REFER", "PRACK", "OPTIONS"};

  // List of sip extensions supported by the container
  private static final String[] EXTENSIONS_SUPPORTED =
      {"MESSAGE", "INFO", "SUBSCRIBE", "NOTIFY", "UPDATE", "PUBLISH", "REFER", "PRACK", "100rel", "STUN", "path", "join", "outbound", "from-change", "gruu"};
  // List of sip rfcs supported by the container
  private static final String[] RFC_SUPPORTED =
      {"3261", "3428", "2976", "3265", "3311", "3903", "3515", "3262", "3489", "3327", "3911", "5626", "4916", "5627"};

  private static final String[] RESPONSES_PER_CLASS_OF_SC =
      {"1XX", "2XX", "3XX", "4XX", "5XX", "6XX", "7XX", "8XX", "9XX"};

  /**
   * Timer task that will gather information about congestion control
   * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A>
   * @deprecated use JAIN SIP Ext Congestion Control innstead
   */
  @Deprecated
  public class CongestionControlTimerTask implements Runnable {

    public void run() {
      if(logger.isDebugEnabled()) {
        logger.debug("CongestionControlTimerTask now running ");
      }
      analyzeQueueCongestionState();
      analyzeMemory();
      if(gatherStatistics) {
        for (SipContext sipContext : applicationDeployed.values()) {
          sipContext.getSipManager().updateStats();
        }
      }
      //TODO wait for JDK 6 new OperatingSystemMXBean
//			analyzeCPU();
    }
  }

  //the logger
  private static final Logger logger = Logger.getLogger(SipApplicationDispatcherImpl.class);

  // app server id
  private String applicationServerId;
  private String applicationServerIdHash;
  // ref back to the sip service
  private SipService sipService = null;
  //the sip factory implementation
  private SipFactoryImpl sipFactoryImpl = null;
  //the sip application router responsible for the routing logic of sip messages to
  //sip servlet applications
  private SipApplicationRouter sipApplicationRouter = null;
  //map of applications deployed
  Map<String, SipContext> applicationDeployed = null;
  //map hashes to app names
  private Map<String, String> mdToApplicationName = null;
  //map app names to hashes
  private Map<String, String> applicationNameToMd = null;
  //List of host names managed by the container
  private Set<String> hostNames = null;

  DispatcherFSM fsm;

  protected SipStack sipStack;
  SipNetworkInterfaceManagerImpl sipNetworkInterfaceManager;
  private DNSServerLocator dnsServerLocator;
  private int dnsTimeout;
  private DNSResolver dnsResolver;

  // stats
  RestcommStatsReporter statsReporter = null;
  MetricRegistry metrics = RestcommStatsReporter.getMetricRegistry();
  //define metric name
  Counter counterCalls = metrics.counter("calls");
  Counter counterSeconds = metrics.counter("seconds");
  Counter counterMessages = metrics.counter("messages");
  //
  private boolean gatherStatistics = true;
  private final AtomicLong requestsProcessed = new AtomicLong(0);
  private final AtomicLong responsesProcessed = new AtomicLong(0);
  final Map<String, AtomicLong> requestsProcessedByMethod = new ConcurrentHashMap<String, AtomicLong>();
  final Map<String, AtomicLong> responsesProcessedByStatusCode = new ConcurrentHashMap<String, AtomicLong>();
  // https://telestax.atlassian.net/browse/MSS-74
  private final AtomicLong requestsSent = new AtomicLong(0);
  private final AtomicLong responsesSent= new AtomicLong(0);
  final Map<String, AtomicLong> requestsSentByMethod = new ConcurrentHashMap<String, AtomicLong>();
  final Map<String, AtomicLong> responsesSentByStatusCode = new ConcurrentHashMap<String, AtomicLong>();

  // congestion control
  private boolean memoryToHigh = false;
  private double maxMemory;
  private int memoryThreshold;
  private int backToNormalMemoryThreshold;
  private boolean rejectSipMessages = false;
  long congestionControlCheckingInterval; //30 sec
  @Deprecated
  protected transient CongestionControlTimerTask congestionControlTimerTask;
  @Deprecated
  protected transient ScheduledFuture congestionControlTimerFuture;
  private CongestionControlPolicy congestionControlPolicy;
  @Deprecated
  private int numberOfMessagesInQueue;
  @Deprecated
  private double percentageOfMemoryUsed;
  @Deprecated
  private int queueSize;
  @Deprecated
  private int backToNormalQueueSize;
  //used for graceful stops and congestion control mechanism (which is now deprecated)
  private ScheduledThreadPoolExecutor asynchronousScheduledThreadPoolExecutor = null;

  // configuration
  private boolean bypassResponseExecutor = true;
  private boolean bypassRequestExecutor = true;
  private int baseTimerInterval = 500; // base timer interval for jain sip tx
  private int t2Interval = 4000; // t2 timer interval for jain sip tx
  private int t4Interval = 5000; // t4 timer interval for jain sip tx
  private int timerDInterval = 32000; // timer D interval for jain sip tx
  private ConcurrencyControlMode concurrencyControlMode;
  public static int APP_ID_HASHING_MAX_LENGTH = 8;
  private static final int NUMBER_OF_TAG_SEPARATORS = 3;
  private int tagHashMaxLength = 8;
  private int callIdMaxLength = -1;

  // This executor is used for async things that don't need to wait on session executors, like CANCEL requests
  // or when the container is configured to execute every request ASAP without waiting on locks (no concurrency control)
  private ThreadPoolExecutor asynchronousExecutor = null;

  // fatcory for dispatching SIP messages
  private MessageDispatcherFactory messageDispatcherFactory;

  //the balancers names to send heartbeat to and our health info
  private Set<SipLoadBalancer> sipLoadBalancers = new CopyOnWriteArraySet<SipLoadBalancer>();

  /**
   *
   */
  public SipApplicationDispatcherImpl() {
    resetStatsCounters();
    applicationDeployed = new ConcurrentHashMap<String, SipContext>();
    mdToApplicationName = new ConcurrentHashMap<String, String>();
    applicationNameToMd = new ConcurrentHashMap<String, String>();
    sipFactoryImpl = new SipFactoryImpl(this);
    hostNames = new CopyOnWriteArraySet<String>();
    sipNetworkInterfaceManager = new SipNetworkInterfaceManagerImpl(this);
    maxMemory = Runtime.getRuntime().maxMemory() / (double) 1024;
    congestionControlPolicy = CongestionControlPolicy.ErrorResponse;
    fsm = new DispatcherFSM(this);
  }

  @Override
  public final void resetStatsCounters() {
    requestsProcessed.set(0);
    responsesProcessed.set(0);
    requestsSent.set(0);
    responsesSent.set(0);
    for (String method : METHODS_SUPPORTED) {
      requestsProcessedByMethod.put(method, new AtomicLong(0));
    }

    for (String classOfSc : RESPONSES_PER_CLASS_OF_SC) {
      responsesProcessedByStatusCode.put(classOfSc, new AtomicLong(0));
    }

    for (String classOfSc : RESPONSES_PER_CLASS_OF_SC) {
      responsesSentByStatusCode.put(classOfSc, new AtomicLong(0));
    }

    for (String method : METHODS_SUPPORTED) {
      requestsSentByMethod.put(method, new AtomicLong(0));
    }

  }

  class InitAction implements DispatcherFSM.Action {

    @Override
    public void execute(DispatcherFSM.Context ctx) {
      //load the sip application router from the javax.servlet.sip.ar.spi.SipApplicationRouterProvider system property
      //and initializes it if present
      String sipApplicationRouterProviderClassName = System.getProperty("javax.servlet.sip.ar.spi.SipApplicationRouterProvider");
      if(sipApplicationRouterProviderClassName != null && sipApplicationRouterProviderClassName.length() > 0) {
        if(logger.isInfoEnabled()) {
          logger.info("Using the javax.servlet.sip.ar.spi.SipApplicationRouterProvider system property to load the application router provider");
        }
        try {
          sipApplicationRouter = ((SipApplicationRouterProvider)
              Class.forName(sipApplicationRouterProviderClassName).newInstance()).getSipApplicationRouter();
        } catch (InstantiationException e) {
          throw new IllegalArgumentException("Impossible to load the Sip Application Router",e);
        } catch (IllegalAccessException e) {
          throw new IllegalArgumentException("Impossible to load the Sip Application Router",e);
        } catch (ClassNotFoundException e) {
          throw new IllegalArgumentException("Impossible to load the Sip Application Router",e);
        } catch (ClassCastException e) {
          throw new IllegalArgumentException("Sip Application Router defined does not implement " + SipApplicationRouterProvider.class.getName(),e);
        }
      } else {
        if(logger.isInfoEnabled()) {
          logger.info("Using the Service Provider Framework to load the application router provider");
        }
        //TODO when moving to JDK 6, use the official http://java.sun.com/javase/6/docs/api/java/util/ServiceLoader.html instead
        //http://grep.codeconsult.ch/2007/10/31/the-java-service-provider-spec-and-sunmiscservice/
        Iterator<SipApplicationRouterProvider> providers = ServiceRegistry.lookupProviders(SipApplicationRouterProvider.class);
        if(providers.hasNext()) {
          sipApplicationRouter = providers.next().getSipApplicationRouter();
        }
      }
      if(sipApplicationRouter == null) {
        throw new IllegalArgumentException("No Sip Application Router Provider could be loaded. " +
            "No jar compliant with JSR 289 Section Section 15.4.2 could be found on the classpath " +
            "and no javax.servlet.sip.ar.spi.SipApplicationRouterProvider system property set");
      }
      if(logger.isInfoEnabled()) {
        logger.info(this + " Using the following Application Router instance: " + sipApplicationRouter);
      }
      sipApplicationRouter.init();
      sipApplicationRouter.applicationDeployed(new ArrayList<String>(applicationDeployed.keySet()));

      // set the DNSServerLocator allowing to support RFC 3263 and do DNS lookups to resolve uris
      if(sipService.getDnsResolverClass() != null && sipService.getDnsResolverClass().trim().length() > 0) {
        if(logger.isInfoEnabled()) {
          logger.info("SipApplicationDispatcher will be using " + sipService.getDnsResolverClass() + " as DNSResolver");
        }
        try {
          // create parameters argument to identify constructor
          Class[] paramTypes = new Class[] {DNSServerLocator.class};
          // get constructor of AddressResolver in order to instantiate
          Constructor dnsResolverConstructor = Class.forName(sipService.getDnsResolverClass()).getConstructor(
              paramTypes);
          // Wrap properties object in order to pass to constructor of AddressResolver
          Object[] conArgs = new Object[] {dnsServerLocator};
          // Creates a new instance of AddressResolver Class with the supplied sipApplicationDispatcher.
          dnsResolver = (DNSResolver) dnsResolverConstructor.newInstance(conArgs);
        } catch (Exception e) {
          logger.error("Couldn't set the DNSResolver " + sipService.getDnsResolverClass(), e);
          throw new IllegalArgumentException(e);
        }
      } else {
        if(logger.isInfoEnabled()) {
          logger.info("Using default MobicentsDNSResolver since none has been specified.");
        }
        dnsResolver = new MobicentsDNSResolver(dnsServerLocator);
      }

      if( oname == null ) {
        try {
          oname=new ObjectName(domain + ":type=SipApplicationDispatcher");
          ((MBeanServer) MBeanServerFactory.findMBeanServer(null).get(0)).registerMBean(SipApplicationDispatcherImpl.this, oname);
          if(logger.isInfoEnabled()) {
            logger.info("Sip Application dispatcher registered under following name " + oname);
          }
        } catch (Exception e) {
          logger.error("Impossible to register the Sip Application dispatcher in domain" + domain, e);
        }
      }
      if(logger.isInfoEnabled()) {
        logger.info("bypassRequestExecutor ? " + bypassRequestExecutor);
        logger.info("bypassResponseExecutor ? " + bypassResponseExecutor);
      }
      if(sipService.getCallIdMaxLength() > 0) {
        callIdMaxLength = sipService.getCallIdMaxLength();
        if(logger.isInfoEnabled()) {
          logger.info("callIdMaxLength ? " + callIdMaxLength);
        }
      }
      int tagHashMaxTotalLength = sipService.getTagHashMaxLength();
      if(tagHashMaxTotalLength > 0) {
        tagHashMaxLength = (tagHashMaxTotalLength - NUMBER_OF_TAG_SEPARATORS) / 4;
        APP_ID_HASHING_MAX_LENGTH = tagHashMaxTotalLength - NUMBER_OF_TAG_SEPARATORS - (tagHashMaxLength * NUMBER_OF_TAG_SEPARATORS);
        if(logger.isInfoEnabled()) {
          logger.info("tagHashMaxLength ? " + tagHashMaxLength);
          logger.info("DEFAULT_TAG_HASHING_MAX_LENGTH ? " + APP_ID_HASHING_MAX_LENGTH);
        }
      }
      applicationServerId = "" + UUID.randomUUID();
      applicationServerIdHash = GenericUtils.hashString(applicationServerId, tagHashMaxLength);

      messageDispatcherFactory = new MessageDispatcherFactory(ctx.dispatcher);
      asynchronousScheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(2, new NamingThreadFactory("sip_servlets_congestion_control"),
          new ThreadPoolExecutor.CallerRunsPolicy());
      asynchronousScheduledThreadPoolExecutor.prestartAllCoreThreads();
      logger.info("AsynchronousThreadPoolExecutor size is " + sipService.getDispatcherThreadPoolSize());
      asynchronousExecutor = new ThreadPoolExecutor(sipService.getDispatcherThreadPoolSize(), 64, 90, TimeUnit.SECONDS,
          new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
        private int threadCount = 0;

        public Thread newThread(Runnable pRunnable) {
          Thread thread = new Thread(pRunnable, String.format("%s-%d",
              "MSS-Executor-Thread", threadCount++));
          thread.setPriority(((SipStackImpl)sipStack).getThreadPriority());
          return thread;
        }
      });
      asynchronousExecutor.setRejectedExecutionHandler(new RejectedExecutionHandler(){

        public void rejectedExecution(Runnable r,
                                      ThreadPoolExecutor executor) {
          logger.warn("Executor job was rejected " + r.toString());

        }

      });

      if (getGatherStatistics()) {
        statsReporter = new RestcommStatsReporter();

        String statisticsServer = Version.getVersionProperty(Version.STATISTICS_SERVER);
        if(statisticsServer == null || !statisticsServer.contains("http")) {
          statisticsServer = Version.DEFAULT_STATISTICS_SERVER;
        }
        //define remote server address (optionally)
        statsReporter.setRemoteServer(statisticsServer);
        String projectName = System.getProperty("RestcommProjectName", "sipservlets");
        String projectType = System.getProperty("RestcommProjectType", "community");
        String projectVersion = System.getProperty("RestcommProjectVersion", Version.getVersionProperty(Version.RELEASE_VERSION));
        if(logger.isDebugEnabled()) {
          logger.debug("Restcomm Stats " + projectName + " " + projectType + " " + projectVersion);
        }
        statsReporter.setProjectName(projectName);
        statsReporter.setProjectType(projectType);
        statsReporter.setVersion(projectVersion);
      }

    }
  }
  @Override
  public void init() throws IllegalArgumentException {
    fsm.fireEvent(fsm.new Event(DispatcherFSM.EventType.INIT));
  }

  @Override
  public void putInService() throws IllegalArgumentException {
    fsm.fireEvent(fsm.new Event(DispatcherFSM.EventType.IN_SERVICE));
  }


  class StartAction implements DispatcherFSM.Action {
    @Override
    public void execute(DispatcherFSM.Context ctx) {
      congestionControlTimerTask = new CongestionControlTimerTask();
      if(congestionControlTimerFuture == null && congestionControlCheckingInterval > 0) {
        congestionControlTimerFuture = asynchronousScheduledThreadPoolExecutor.scheduleWithFixedDelay(congestionControlTimerTask, congestionControlCheckingInterval, congestionControlCheckingInterval, TimeUnit.MILLISECONDS);
        if(logger.isInfoEnabled()) {
          logger.info("Congestion control background task started and checking every " + congestionControlCheckingInterval + " milliseconds.");
        }
      } else {
        if(logger.isInfoEnabled()) {
          logger.info("No Congestion control background task started since the checking interval is equals to " + congestionControlCheckingInterval + " milliseconds.");
        }
      }
      if (statsReporter != null) {
        //define periodicy - default to once a day
        statsReporter.start(86400, TimeUnit.SECONDS);
      }

      Version.printVersion();
      // outbound interfaces set here and not in sipstandardcontext because
      // depending on jboss or tomcat context can be started before or after
      // connectors
      resetOutboundInterfaces();

      // Starting the SIP Stack right before we notify the apps that the container is ready
      // to serve and dispatch SIP Messages.
      // In addition, the LB Heartbeat will be started only when apps are ready
      try {
        startSipStack();
      } catch (Exception e) {
        throw new IllegalStateException("The SIP Stack couldn't be started " , e);
      }


      if(logger.isDebugEnabled()) {
        logger.debug("SipApplicationDispatcher Started");
      }
    }

  }
  @Override
  public void start() {
    fsm.fireEvent(fsm .new Event(DispatcherFSM.EventType.START));
  }

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#stopGracefully(long)
   */
  public void stopGracefully(long timeToWait) {
    sipService.stopGracefully(timeToWait);
  }

  class StopAction implements DispatcherFSM.Action {

    @Override
    public void execute(DispatcherFSM.Context ctx) {
      asynchronousScheduledThreadPoolExecutor.shutdownNow();
      asynchronousExecutor.shutdownNow();
      sipApplicationRouter.destroy();

      stopSipStack();

      if (statsReporter != null) {
        try {
          statsReporter.stop();
        } catch (RuntimeException lExp) {
          logger.warn("License not found", lExp);
        }
      }

      if(oname != null) {
        try {
          ((MBeanServer) MBeanServerFactory.findMBeanServer(null).get(0)).unregisterMBean(oname);
        } catch (Exception e) {
          logger.error("Impossible to register the Sip Application dispatcher in domain" + domain, e);
        }
      }
      if(logger.isDebugEnabled()) {
        logger.debug("SipApplicationDispatcher Stopped");
      }        }

  }

  public void stop() {
    fsm.fireEvent(fsm .new Event(DispatcherFSM.EventType.STOP));
  }

  class AddAppAction implements DispatcherFSM.Action {


    @Override
    public void execute(DispatcherFSM.Context ctx) {
      SipContext sipApplication = (SipContext)ctx.lastEvent.data.get(CONTEXT_EV_DATA);
      String sipApplicationName = sipApplication.getApplicationName();

      if (logger.isDebugEnabled()) {
        logger.debug("Adding the following sip servlet application " + sipApplicationName + ", SipContext=" + sipApplication);
      }
      if (sipApplicationName == null) {
        throw new IllegalArgumentException("Something when wrong while initializing a sip servlets or converged application ");
      }
      if (sipApplication == null) {
        throw new IllegalArgumentException("Something when wrong while initializing the following application " + sipApplicationName);
      }
      // Issue 1417 http://code.google.com/p/mobicents/issues/detail?id=1417
      // Deploy 2 applications with the same app-name should fail
      SipContext app = applicationDeployed.get(sipApplicationName);
      if (app != null) {
        logger.error("An application with the app name " + sipApplicationName + " is already deployed under the following context " + app.getPath());
        throw new IllegalStateException("An application with the app name " + sipApplicationName + " is already deployed under the following context " + app.getPath());
      }
      //if the application has not set any concurrency control mode, we default to the container wide one
      if (sipApplication.getConcurrencyControlMode() == null) {
        sipApplication.setConcurrencyControlMode(concurrencyControlMode);
        if (logger.isInfoEnabled()) {
          logger.info("No concurrency control mode for application " + sipApplicationName + " , defaulting to the container wide one : " + concurrencyControlMode);
        }
      } else if (logger.isInfoEnabled()) {
        logger.info("Concurrency control mode for application " + sipApplicationName + " is " + sipApplication.getConcurrencyControlMode());
      }
      sipApplication.getServletContext().setAttribute(ConcurrencyControlMode.class.getCanonicalName(), sipApplication.getConcurrencyControlMode());

      applicationDeployed.put(sipApplicationName, sipApplication);

      String hash = GenericUtils.hashString(sipApplicationName, tagHashMaxLength);
      mdToApplicationName.put(hash, sipApplicationName);
      applicationNameToMd.put(sipApplicationName, hash);

      List<String> newlyApplicationsDeployed = new ArrayList<String>();
      newlyApplicationsDeployed.add(sipApplicationName);
      if (sipApplicationRouter != null) {
        // https://code.google.com/p/sipservlets/issues/detail?id=277
        // sipApplicationRouter may not be initialized yet if container is fast to boot
        sipApplicationRouter.applicationDeployed(newlyApplicationsDeployed);
      }

      if (logger.isInfoEnabled()) {
        logger.info(this + " the following sip servlet application has been added : " + sipApplicationName);
      }
      if (logger.isInfoEnabled()) {
        logger.info("It contains the following Sip Servlets : ");
        for (String servletName : sipApplication.getChildrenMap().keySet()) {
          logger.info("SipApplicationName : " + sipApplicationName + "/ServletName : " + servletName);
        }
        if (sipApplication.getSipRubyController() != null) {
          logger.info("It contains the following Sip Ruby Controller : " + sipApplication.getSipRubyController());
        }
      }
    }

  }

  static final String CONTEXT_EV_DATA = "Context";
  /**
   * {@inheritDoc}
   */
  public void addSipApplication(String sipApplicationName, SipContext sipApplication) {
    DispatcherFSM.Event ev = fsm.new Event(DispatcherFSM.EventType.ADD_APP);
    ev.data.put(CONTEXT_EV_DATA, sipApplication);
    fsm.fireEvent(ev);
  }

  static final String APP_NAME = "AppName";

  class RemoveApp implements DispatcherFSM.Action {

    @Override
    public void execute(DispatcherFSM.Context ctx) {
      String sipApplicationName = (String) ctx.lastEvent.data.get(APP_NAME);
      SipContext sipContext = applicationDeployed.remove(sipApplicationName);
      List<String> applicationsUndeployed = new ArrayList<String>();
      applicationsUndeployed.add(sipApplicationName);
      sipApplicationRouter.applicationUndeployed(applicationsUndeployed);
      if (sipContext != null) {
        sipContext.getSipManager().removeAllSessions();
      }
      String hash = GenericUtils.hashString(sipApplicationName, tagHashMaxLength);
      mdToApplicationName.remove(hash);
      applicationNameToMd.remove(sipApplicationName);
      if (logger.isInfoEnabled()) {
        logger.info("the following sip servlet application has been removed : " + sipApplicationName);
      }
      ctx.lastEvent.data.put(CONTEXT_EV_DATA, sipContext);
    }

  }

  /**
   * {@inheritDoc}
   */
  public SipContext removeSipApplication(String sipApplicationName) {
    DispatcherFSM.Event ev = fsm.new Event(DispatcherFSM.EventType.REMOVE_APP);
    ev.data.put(APP_NAME, sipApplicationName);
    fsm.fireEvent(ev);
    return (SipContext) ev.data.get(CONTEXT_EV_DATA);
  }


  /*
   * (non-Javadoc)
   * @see javax.sip.SipListener#processIOException(javax.sip.IOExceptionEvent)
   */
  public void processIOException(IOExceptionEvent event) {
    if(event instanceof IOExceptionEventExt  && ((IOExceptionEventExt)event).getReason() == gov.nist.javax.sip.IOExceptionEventExt.Reason.KeepAliveTimeout) {
      IOExceptionEventExt keepAliveTimeout = ((IOExceptionEventExt)event);

      SipConnector connector = findSipConnector(
          keepAliveTimeout.getLocalHost(),
          keepAliveTimeout.getLocalPort(),
          keepAliveTimeout.getTransport());

      if(connector != null) {
        for (SipContext sipContext : applicationDeployed.values()) {
          final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
          sipContext.enterSipContext();
          try {
            for (SipConnectorListener connectorListener : sipContext.getListeners().getSipConnectorListeners()) {
              try {
                connectorListener.onKeepAliveTimeout(connector,
                    keepAliveTimeout.getPeerHost(),
                    keepAliveTimeout.getPeerPort());
              } catch (Throwable t) {
                logger.error("SipErrorListener threw exception", t);
              }
            }
          } finally {
            sipContext.exitSipContext(oldClassLoader);
          }
        }
//		        return;
      }
    }
    if(dnsServerLocator != null && event.getSource() instanceof ClientTransaction) {
      ClientTransaction ioExceptionTx = (ClientTransaction) event.getSource();
      if(ioExceptionTx.getApplicationData() != null) {
        SipServletMessageImpl sipServletMessageImpl = ((TransactionApplicationData)ioExceptionTx.getApplicationData()).getSipServletMessage();
        if(sipServletMessageImpl != null && sipServletMessageImpl instanceof SipServletRequestImpl) {
          if(logger.isDebugEnabled()) {
            logger.debug("An IOException occured on " + event.getHost() + ":" + event.getPort() + "/" + event.getTransport() + " for source " + event.getSource() + ", trying to visit next hop as per RFC3263");
          }
          if(((SipServletRequestImpl)sipServletMessageImpl).visitNextHop()) {
            return;
          }
        }
      }
    }
    logger.error("An IOException occured on " + event.getHost() + ":" + event.getPort() + "/" + event.getTransport() + " for source " + event.getSource());
  }

  /*
   * Gives the number of pending messages in all queues for all concurrency control modes.
   */
  public int getNumberOfPendingMessages() {
    return this.asynchronousExecutor.getQueue().size();
//		int size = 0;
//		Iterator<SipContext> applicationsIterator = this.applicationDeployed
//				.values().iterator();
//		boolean noneModeAlreadyCounted = false;
//		while (applicationsIterator.hasNext()) {
//			SipContext context = applicationsIterator.next();
//			SipManager manager = (SipManager) context
//					.getManager();
//			if(context.getConcurrencyControlMode().equals(
//					ConcurrencyControlMode.None) && !noneModeAlreadyCounted) {
//				size = this.asynchronousExecutor.getQueue().size();
//				noneModeAlreadyCounted = true;
//			} else if (context.getConcurrencyControlMode().equals(
//					ConcurrencyControlMode.SipApplicationSession)) {
//				Iterator<MobicentsSipApplicationSession> sessionIterator = manager
//						.getAllSipApplicationSessions();
//				while (sessionIterator.hasNext()) {
//					size += sessionIterator.next().getExecutorService()
//							.getQueue().size();
//				}
//			} else if (context.getConcurrencyControlMode().equals(
//					ConcurrencyControlMode.SipSession)) {
//				Iterator<MobicentsSipSession> sessionIterator = manager
//						.getAllSipSessions();
//				while (sessionIterator.hasNext()) {
//					size += sessionIterator.next().getExecutorService()
//							.getQueue().size();
//				}
//			}
//		}
//
//		return size;
  }

  private void analyzeQueueCongestionState() {
    this.numberOfMessagesInQueue = getNumberOfPendingMessages();
    if(rejectSipMessages) {
      if(numberOfMessagesInQueue  < backToNormalQueueSize) {
        String message = "Number of pending messages in the queues : " + numberOfMessagesInQueue + " < to the back to normal queue Size : " + backToNormalQueueSize;
        logger.warn(message + " => stopping to reject requests");
        rejectSipMessages = false;
        final CongestionControlEvent congestionControlEvent = new CongestionStoppedEvent(
            org.mobicents.javax.servlet.CongestionControlEvent.Reason.Queue, message);
        callbackContainerEventListener(congestionControlEvent);
      }
    } else {
      if(numberOfMessagesInQueue > queueSize) {
        String message = "Number of pending messages in the queues : " + numberOfMessagesInQueue + " > to the queue Size : " + queueSize;
        logger.warn(message + " => starting to reject requests");
        rejectSipMessages = true;
        final CongestionControlEvent congestionControlEvent = new CongestionStartedEvent(
            org.mobicents.javax.servlet.CongestionControlEvent.Reason.Queue, message);
        callbackContainerEventListener(congestionControlEvent);
      }
    }
  }

  private void analyzeMemory() {
    Runtime runtime = Runtime.getRuntime();

    double allocatedMemory = runtime.totalMemory() / (double) 1024;
    double freeMemory = runtime.freeMemory() / (double) 1024;

    double totalFreeMemory = freeMemory + (maxMemory - allocatedMemory);
    this.percentageOfMemoryUsed=  (((double)100) - ((totalFreeMemory / maxMemory) * ((double)100)));

    if(memoryToHigh) {
      if(percentageOfMemoryUsed < backToNormalMemoryThreshold) {
        String message = "Memory used: " + percentageOfMemoryUsed + "% < to the back to normal memory threshold : " + backToNormalMemoryThreshold+ "%";
        logger.warn(message + " => stopping to reject requests");
        memoryToHigh = false;
        final CongestionControlEvent congestionControlEvent = new CongestionStoppedEvent(
            org.mobicents.javax.servlet.CongestionControlEvent.Reason.Memory, message);
        callbackContainerEventListener(congestionControlEvent);
      }
    } else {
      if(percentageOfMemoryUsed > memoryThreshold) {
        String message = "Memory used: " + percentageOfMemoryUsed + "% > to the memory threshold : " + memoryThreshold+ "%";
        logger.warn(message + " => starting to reject requests");
        memoryToHigh = true;
        final CongestionControlEvent congestionControlEvent = new CongestionStartedEvent(
            org.mobicents.javax.servlet.CongestionControlEvent.Reason.Memory, message);
        callbackContainerEventListener(congestionControlEvent);
      }
    }
  }

  /*
   * (non-Javadoc)
   * @see javax.sip.SipListener#processRequest(javax.sip.RequestEvent)
   */
  public void processRequest(RequestEvent requestEvent) {
    final SipProvider sipProvider = (SipProvider)requestEvent.getSource();
    ServerTransaction requestTransaction =  requestEvent.getServerTransaction();
    final Dialog dialog = requestEvent.getDialog();
    if(logger.isDebugEnabled()) {
      logger.debug("processRequest - dialog=" + dialog);
      if (dialog != null){
        logger.debug("processRequest - dialog.getDialogId()=" + dialog.getDialogId());
      }
    }
    final Request request = requestEvent.getRequest();
    // self routing makes the application data cloned, so we make sure to nullify it
    ((MessageExt)request).setApplicationData(null);
    final String requestMethod = request.getMethod();

    final RouteHeader routeHeader = (RouteHeader) request
        .getHeader(RouteHeader.NAME);

    // congestion control is done here only if we drop messages to avoid generating STX
    if(CongestionControlPolicy.DropMessage.equals(congestionControlPolicy) && controlCongestion(request, null, dialog, routeHeader, sipProvider)) {
      return;
    }

    if((rejectSipMessages || memoryToHigh) && CongestionControlPolicy.DropMessage.equals(congestionControlPolicy)) {
      String method = requestEvent.getRequest().getMethod();
      boolean goodMethod = method.equals(Request.ACK) || method.equals(Request.PRACK) || method.equals(Request.BYE) || method.equals(Request.CANCEL) || method.equals(Request.UPDATE) || method.equals(Request.INFO);
      if(logger.isDebugEnabled()) {
        logger.debug("congestion control good method " + goodMethod + ", dialog "  + dialog + " routeHeader " + routeHeader);
      }
      if(!goodMethod) {
        if(dialog == null && (routeHeader == null || ((Parameters)routeHeader.getAddress().getURI()).getParameter(MessageDispatcher.RR_PARAM_PROXY_APP) == null)) {
          logger.error("dropping request, memory is too high or too many messages present in queues");
          return;
        }
      }
    }

    try {
      if(logger.isDebugEnabled()) {
        logger.debug("sipApplicationDispatcher " + this + ", Got a request event "  + request.toString());
      }
      if (!Request.ACK.equals(requestMethod) && requestTransaction == null ) {
        try {
          //folsson fix : Workaround broken Cisco 7940/7912
          if(request.getHeader(MaxForwardsHeader.NAME) == null){
            request.setHeader(SipFactoryImpl.headerFactory.createMaxForwardsHeader(70));
          }
          requestTransaction = sipProvider.getNewServerTransaction(request);
          JainSipUtils.setTransactionTimers(((TransactionExt)requestTransaction), this);
        } catch ( TransactionUnavailableException tae) {
          logger.error("cannot get a new Server transaction for this request " + request, tae);
          // Sends a 500 Internal server error and stops processing.
          MessageDispatcher.sendErrorResponse(this, Response.SERVER_INTERNAL_ERROR, requestTransaction, request, sipProvider);
          return;
        } catch ( TransactionAlreadyExistsException taex ) {
          // This is a retransmission so just return.
          return;
        }
      }
      final ServerTransaction transaction = requestTransaction;

      if(logger.isDebugEnabled()) {
        logger.debug("ServerTx ref "  + transaction);
        logger.debug("Dialog ref "  + dialog);
      }

      final SipServletRequestImpl sipServletRequest = (SipServletRequestImpl) sipFactoryImpl.getMobicentsSipServletMessageFactory().createSipServletRequest(
          request,
          null,
          transaction,
          dialog,
          JainSipUtils.DIALOG_CREATING_METHODS.contains(requestMethod));
      updateRequestsStatistics(request, true);

      if(logger.isDebugEnabled()) {
        logger.debug("processRequest - routing state=" + sipServletRequest.getRoutingState());
      }

      // Check if the request is meant for me. If so, strip the topmost
      // Route header.

      //Popping the router header if it's for the container as
      //specified in JSR 289 - Section 15.8
      if(!isRouteExternal(routeHeader)) {
        if(logger.isDebugEnabled()) {
          logger.debug("processRequest - not external - =routeHeader" + routeHeader);
        }
        request.removeFirst(RouteHeader.NAME);
        sipServletRequest.setPoppedRoute(routeHeader);
        final Parameters poppedAddress = (Parameters)routeHeader.getAddress().getURI();
        if(poppedAddress.getParameter(MessageDispatcher.RR_PARAM_PROXY_APP) != null ||
            // Issue 2850 :	Use Request-URI custom Mobicents parameters to route request for misbehaving agents, workaround for Cisco-SIPGateway/IOS-12.x user agent
            (request.getRequestURI() instanceof javax.sip.address.SipURI && ((Parameters)request.getRequestURI()).getParameter(MessageDispatcher.RR_PARAM_PROXY_APP) != null)) {
          if(logger.isDebugEnabled()) {
            logger.debug("the request is for a proxy application, thus it is a subsequent request ");
          }
          sipServletRequest.setRoutingState(RoutingState.SUBSEQUENT);
        }
        if(transaction != null) {
          if(logger.isDebugEnabled()) {
            logger.debug("processRequest - transaction not null, transaction.getDialog()=" + transaction.getDialog());
            if (transaction.getDialog() != null){
              logger.debug("processRequest - transaction dialog not null, transaction.getDialog().getDialogId()=" + transaction.getDialog().getDialogId());
            }
          }
          TransactionApplicationData transactionApplicationData = (TransactionApplicationData)transaction.getApplicationData();
          if(transactionApplicationData != null && transactionApplicationData.getInitialPoppedRoute() == null) {
            if (transaction.getDialog() != null){
              logger.debug("processRequest - setInitialPoppedRoute, routeHeader.getAddress()=" + routeHeader.getAddress());
            }
            transactionApplicationData.setInitialPoppedRoute(new AddressImpl(routeHeader.getAddress(), null, ModifiableRule.NotModifiable));
          }
        }
      }
      if(logger.isDebugEnabled()) {
        logger.debug("Routing State " + sipServletRequest.getRoutingState());
      }

      try {
        // congestion control is done here so that the STX is created and a response can be generated back
        // and that
        if(controlCongestion(request, sipServletRequest, dialog, routeHeader, sipProvider)) {
          return;
        }

        if(logger.isDebugEnabled()) {
          logger.debug("processRequest - dispatching request with sipServletRequest.getAppSessionId()=" + sipServletRequest.getAppSessionId());
        }

        messageDispatcherFactory.getRequestDispatcher(sipServletRequest, this).
            dispatchMessage(sipProvider, sipServletRequest);
      } catch (DispatcherException e) {
        // Change log level from Error to debug: https://github.com/RestComm/jain-sip/issues/116
        if (logger.isDebugEnabled()) {
          logger.debug("Unexpected exception while processing request " + request,e);
        }
        // Sends an error response if the subsequent request is not an ACK (otherwise it violates RF3261) and stops processing.
        if(!Request.ACK.equalsIgnoreCase(requestMethod)) {
          MessageDispatcher.sendErrorResponse(this, e.getErrorCode(), sipServletRequest, sipProvider);
        }
        return;
      } catch (Throwable e) {
        // Change log level from Error to debug: https://github.com/RestComm/jain-sip/issues/116
        if (logger.isDebugEnabled()) {
          logger.debug("Unexpected exception while processing request " + request,e);
        }
        // Sends a 500 Internal server error if the subsequent request is not an ACK (otherwise it violates RF3261) and stops processing.
        if(!Request.ACK.equalsIgnoreCase(requestMethod)) {
          MessageDispatcher.sendErrorResponse(this, Response.SERVER_INTERNAL_ERROR, sipServletRequest, sipProvider);
        }
        return;
      }
    } catch (Throwable e) {
      // Change log level from Error to debug: https://github.com/RestComm/jain-sip/issues/116
      if (logger.isDebugEnabled()) {
        logger.debug("Unexpected exception while processing request " + request,e);
      }
      // Sends a 500 Internal server error if the subsequent request is not an ACK (otherwise it violates RF3261) and stops processing.
      if(!Request.ACK.equalsIgnoreCase(request.getMethod())) {
        MessageDispatcher.sendErrorResponse(this, Response.SERVER_INTERNAL_ERROR, requestTransaction, request, sipProvider);
      }
      return;
    }
  }

  private void callbackContainerEventListener(ContainerEvent congestionControlEvent) {
    for (SipContext sipContext : applicationDeployed.values()) {
      sipContext.getListeners().callbackContainerListener(congestionControlEvent);
    }
  }

  private static final String THROTTLED_RESPONSE = "org.mobicents.servlet.sip.THROTTLED_RESPONSE";

  private boolean controlCongestion(Request request, SipServletRequestImpl sipServletRequest, Dialog dialog, RouteHeader routeHeader, SipProvider sipProvider) {
    if(rejectSipMessages || memoryToHigh) {
      String method = request.getMethod();
      boolean goodMethod = method.equals(Request.ACK) || method.equals(Request.PRACK) || method.equals(Request.BYE) || method.equals(Request.CANCEL) || method.equals(Request.UPDATE) || method.equals(Request.INFO);
      if(logger.isDebugEnabled()) {
        logger.debug("congestion control good method " + goodMethod + ", dialog "  + dialog + " routeHeader " + routeHeader);
      }
      if(!goodMethod) {
        if(dialog == null && (routeHeader == null || ((Parameters)routeHeader.getAddress().getURI()).getParameter(MessageDispatcher.RR_PARAM_PROXY_APP) == null)) {
          if(CongestionControlPolicy.DropMessage.equals(congestionControlPolicy)) {
            logger.error("dropping request, memory is too high or too many messages present in queues");
            return true;
          }
          SipServletResponse sipServletResponse = null;
          String message = null;
          if(rejectSipMessages) {
            message = "Number of pending messages in the queues : " + numberOfMessagesInQueue + " > to the queue Size : " + queueSize;
          } else if (memoryToHigh) {
            message = "Memory used: " + percentageOfMemoryUsed + "% > to the memory threshold : " + memoryThreshold + "%";
          }
          final RequestThrottledEvent congestionControlEvent = new RequestThrottledEvent(sipServletRequest,
              org.mobicents.javax.servlet.CongestionControlEvent.Reason.Memory, message);

          for (SipContext sipContext : applicationDeployed.values()) {
            final ContainerListener containerListener =
                sipContext.getListeners().getContainerListener();

            if(containerListener != null) {
              final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
              try {
                sipContext.enterSipContext();
                try {

                  containerListener.sendEvent(congestionControlEvent, sipContext.getServletContext());
                  sipServletResponse = (SipServletResponse) sipContext.getServletContext().getAttribute(THROTTLED_RESPONSE);
                  sipServletRequest.getServletContext().removeAttribute(THROTTLED_RESPONSE);
                } catch (Throwable t) {
                  logger.error("ContainerListener threw exception", t);
                }
              } finally {
                sipContext.exitSipContext(oldClassLoader);
              }

              if(sipServletResponse != null) {
                // container listener generated a response, we send it
                try{
                  ((ServerTransaction)sipServletRequest.getTransaction()).sendResponse(((SipServletResponseImpl)sipServletResponse).getResponse());
                  sipFactoryImpl.getSipApplicationDispatcher().updateResponseStatistics(((SipServletResponseImpl)sipServletResponse).getResponse(), false);
//									sipServletResponse.send();
                } catch (Exception e) {
                  logger.error("Problem while sending the error response " + sipServletResponse + " to the following request "
                      + request.toString(), e);
                }
                return true;
              }
            }
          }
          // no application implements the container listener or the container listener didn't generate any responses so we send back a generic one.
          if(sipServletResponse == null) {
            MessageDispatcher.sendErrorResponse(this, Response.SERVICE_UNAVAILABLE, (ServerTransaction) sipServletRequest.getTransaction(), request, sipProvider);
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * @param requestMethod
   */
  public void updateRequestsStatistics(final Request request, final boolean processed) {
    if(gatherStatistics) {
      AtomicLong requestsStats = null;
      if(processed) {
        requestsStats = requestsProcessed;
      } else {
        requestsStats = requestsSent;
      }
      requestsStats.incrementAndGet();
      final String method = request.getMethod();
      AtomicLong requestsStatsMethod = null;
      if(processed) {
        requestsStatsMethod = requestsProcessedByMethod.get(method);
      } else {
        requestsStatsMethod = requestsSentByMethod.get(method);
      }
      if(requestsStatsMethod == null) {
        if(processed) {
          requestsProcessedByMethod.put(method, new AtomicLong());
        } else {
          requestsSentByMethod.put(method, new AtomicLong());
        }
      } else {
        requestsStatsMethod.incrementAndGet();
      }
    }
  }

  /**
   * @param requestMethod
   */
  public void updateResponseStatistics(final Response response, final boolean processed) {
    if(gatherStatistics) {
      AtomicLong responsesStats = null;
      Map<String, AtomicLong> responsesStatsStatusCode = null;
      if(processed) {
        responsesStats = responsesProcessed;
        responsesStatsStatusCode = responsesProcessedByStatusCode;
      } else {
        responsesStats = responsesSent;
        responsesStatsStatusCode = responsesSentByStatusCode;
      }
      responsesStats.incrementAndGet();
      final int statusCode = response.getStatusCode();
      int statusCodeDiv = statusCode / 100;
      switch (statusCodeDiv) {
        case 1:
          responsesStatsStatusCode.get("1XX").incrementAndGet();
          break;
        case 2:
          responsesStatsStatusCode.get("2XX").incrementAndGet();
          break;
        case 3:
          responsesStatsStatusCode.get("3XX").incrementAndGet();
          break;
        case 4:
          responsesStatsStatusCode.get("4XX").incrementAndGet();
          break;
        case 5:
          responsesStatsStatusCode.get("5XX").incrementAndGet();
          break;
        case 6:
          responsesStatsStatusCode.get("6XX").incrementAndGet();
          break;
        case 7:
          responsesStatsStatusCode.get("7XX").incrementAndGet();
          break;
        case 8:
          responsesStatsStatusCode.get("8XX").incrementAndGet();
          break;
        case 9:
          responsesStatsStatusCode.get("9XX").incrementAndGet();
          break;
      }
    }
  }

  @Override
  public void incCalls() {
    counterCalls.inc();
  }

  @Override
  public void incMessages() {
    counterMessages.inc();
  }

  @Override
  public void incSeconds(long seconds) {
    counterSeconds.inc(seconds);
  }

  /*
   * (non-Javadoc)
   * @see javax.sip.SipListener#processResponse(javax.sip.ResponseEvent)
   */
  public void processResponse(ResponseEvent responseEvent) {
    final ResponseEventExt responseEventExt = (ResponseEventExt) responseEvent;
    final Response response = responseEventExt.getResponse();

    if(logger.isDebugEnabled()) {
      logger.debug("Response " + response.toString());
    }

    final CSeqHeader cSeqHeader = (CSeqHeader)response.getHeader(CSeqHeader.NAME);
    //if this is a response to a cancel, the response is dropped
    if(Request.CANCEL.equalsIgnoreCase(cSeqHeader.getMethod())) {
      if(logger.isDebugEnabled()) {
        logger.debug("the response is dropped accordingly to JSR 289 " +
            "since this a response to a CANCEL");
      }
      return;
    }
    // self routing makes the application data cloned, so we make sure to nullify it
    ((MessageExt)response).setApplicationData(null);

    updateResponseStatistics(response, true);
    ClientTransaction clientTransaction = responseEventExt.getClientTransaction();
    final Dialog dialog = responseEventExt.getDialog();
    final boolean isForkedResponse = responseEventExt.isForkedResponse();
    final boolean isRetransmission = responseEventExt.isRetransmission();
    final ClientTransactionExt originalTransaction = responseEventExt.getOriginalTransaction();
    if(logger.isDebugEnabled()) {
      logger.debug("is Forked Response " + isForkedResponse);
      logger.debug("is Retransmission " + isRetransmission);
      logger.debug("Client Transaction " + clientTransaction);
      logger.debug("Original Transaction " + originalTransaction);
      logger.debug("Dialog " + dialog);
      if(dialog != null) {
        logger.debug("Dialog State " + dialog.getState());
      }
    }
    // Issue 1468 : Handling forking
    if(isForkedResponse && originalTransaction != null && !responseEventExt.isRetransmission()) {
      final Dialog defaultDialog = originalTransaction.getDefaultDialog();
      final Dialog orginalTransactionDialog = originalTransaction.getDialog();
      if(logger.isDebugEnabled()) {
        logger.debug("Original Transaction Dialog " + orginalTransactionDialog);
        logger.debug("Original Transaction Default Dialog " + defaultDialog);
      }
      clientTransaction = originalTransaction;
    }

    // Transate the response to SipServletResponse
    final SipServletResponseImpl sipServletResponse = (SipServletResponseImpl) sipFactoryImpl.getMobicentsSipServletMessageFactory().createSipServletResponse(
        response,
        clientTransaction,
        null,
        dialog,
        true,
        isRetransmission);
    try {
      messageDispatcherFactory.getResponseDispatcher(sipServletResponse, this).
          dispatchMessage(((SipProvider)responseEvent.getSource()), sipServletResponse);
    } catch (Throwable e) {
      logger.error("An unexpected exception happened while routing the response " +  sipServletResponse, e);
      return;
    }
  }

  /*
   * (non-Javadoc)
   * @see javax.sip.SipListener#processDialogTerminated(javax.sip.DialogTerminatedEvent)
   */
  public void processDialogTerminated(final DialogTerminatedEvent dialogTerminatedEvent) {
    final Dialog dialog = dialogTerminatedEvent.getDialog();
    if(logger.isDebugEnabled()) {
      logger.debug("Dialog Terminated => dialog Id : " + dialogTerminatedEvent.getDialog().getDialogId());
    }

    getAsynchronousExecutor().execute(new Runnable() {
      // https://github.com/RestComm/sip-servlets/issues/107 guard against NPEon concurrent cleanup
      final TransactionApplicationData dialogAppData = (TransactionApplicationData) dialog.getApplicationData();
      public void run() {
        try {
          boolean appDataFound = false;
          TransactionApplicationData txAppData = null;
          if(dialogAppData != null) {
            if(dialogAppData.getSipServletMessage() == null) {
              Transaction transaction = dialogAppData.getTransaction();
              if(transaction != null && transaction.getApplicationData() != null) {
                txAppData = (TransactionApplicationData) transaction.getApplicationData();
                txAppData.cleanUp();
              }
            } else {
              MobicentsSipSessionKey sipSessionKey = dialogAppData.getSipSessionKey();
              tryToInvalidateSession(sipSessionKey, false);
            }
            dialogAppData.cleanUp();
            // since the stack doesn't nullify the app data, we need to do it to let go of the refs
            dialog.setApplicationData(null);
          }
          if(!appDataFound && logger.isDebugEnabled()) {
            logger.debug("no application data for this dialog " + dialog.getDialogId());
          }
        } catch (Exception e) {
          logger.error("Problem handling dialog termination", e);
        }
      }
    });

  }

  /**
   * @param sipSessionImpl
   */
  private void tryToInvalidateSession(MobicentsSipSessionKey sipSessionKey, boolean invalidateProxySession) {
    if (logger.isDebugEnabled()){
      logger.debug("tryToInvalidateSession - sipSessionKey.getCallId()=" + sipSessionKey.getCallId() + ", sipSessionKey.getFromTag()=" + sipSessionKey.getFromTag() + ", sipSessionKey.getToTag()=" + sipSessionKey.getToTag() + ", sipSessionKey.getApplicationName()=" + sipSessionKey.getApplicationName());
    }
    //the key can be null if the application already invalidated the session
    if(sipSessionKey != null) {
      SipContext sipContext = findSipApplication(sipSessionKey.getApplicationName());
      //the context can be null if the server is being shutdown
      if(sipContext != null) {
        final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
          sipContext.enterSipContext();
          final SipManager sipManager = sipContext.getSipManager();
          final SipApplicationSessionKey sipApplicationSessionKey = SessionManagerUtil.getSipApplicationSessionKey(
              sipSessionKey.getApplicationName(),
              sipSessionKey.getApplicationSessionId(),
              null);

          MobicentsSipSession sipSessionImpl = null;
          MobicentsSipApplicationSession sipApplicationSession = null;
          if(sipManager instanceof DistributableSipManager) {
            // we check only locally if the sessions are present, no need to check in the cache since
            // what triggered this method call was either a transaction or a dialog terminating or timeout
            // so the sessions should be present locally, if they are not it means that it has already been invalidated
            // perf optimization and fix for Issue 1688 : MSS HA on AS5 : Version is null Exception occurs sometimes
            DistributableSipManager distributableSipManager = (DistributableSipManager) sipManager;
            sipApplicationSession = distributableSipManager.getSipApplicationSession(sipApplicationSessionKey, false, true);
            sipSessionImpl = distributableSipManager.getSipSession(sipSessionKey, false, sipFactoryImpl, sipApplicationSession, true);
          } else {
            sipApplicationSession = sipManager.getSipApplicationSession(sipApplicationSessionKey, false);
            sipSessionImpl = sipManager.getSipSession(sipSessionKey, false, sipFactoryImpl, sipApplicationSession);
          }

          if(sipSessionImpl != null) {
            final MobicentsProxy proxy = sipSessionImpl.getProxy();
            if(!invalidateProxySession &&
                (proxy == null || (proxy != null &&
                    ((proxy.getFinalBranchForSubsequentRequests() != null && (!proxy.getFinalBranchForSubsequentRequests().getRecordRoute()) ||
                        proxy.isTerminationSent())))))  {
              if(logger.isDebugEnabled()) {
                if(proxy != null) {
                  logger.debug("try to Invalidate Proxy session if it is non record routing " + proxy.getFinalBranchForSubsequentRequests().getRecordRoute() + " or termination " + proxy.isTerminationSent() + " has been sent " + sipSessionKey);
                } else {
                  logger.debug("Non Proxy session : invalidating");
                }
              }
              invalidateProxySession = true;
            }
            // If this is a client transaction no need to invalidate proxy session http://code.google.com/p/mobicents/issues/detail?id=1024
            if(!invalidateProxySession) {
              if(logger.isDebugEnabled()) {
                logger.debug("don't Invalidate Proxy session");
              }
              return;
            }
            boolean batchStarted = false;
            try {
              sipContext.enterSipApp(sipApplicationSession, sipSessionImpl, false, true);
              batchStarted = sipContext.enterSipAppHa(true);
              if(logger.isDebugEnabled()) {
                logger.debug("sip session " + sipSessionKey + " is valid ? :" + sipSessionImpl.isValidInternal());
                if(sipSessionImpl.isValidInternal()) {
                  // https://code.google.com/p/sipservlets/issues/detail?id=279
                  logger.debug("Sip session " + sipSessionKey + " is ready to be invalidated ? :" + sipSessionImpl.isReadyToInvalidateInternal());
                }
              }
              boolean b2buaOrphaned = sipSessionImpl.isB2BUAOrphan();
              if(sipSessionImpl.isValidInternal() && (
                  // https://code.google.com/p/sipservlets/issues/detail?id=279
                  sipSessionImpl.isReadyToInvalidateInternal() ||
                      b2buaOrphaned)) {
                sipSessionImpl.onTerminatedState();
              }
            } finally {
              sipContext.exitSipAppHa(null, null, batchStarted);
              sipContext.exitSipApp(sipApplicationSession, sipSessionImpl);
            }
          } else {
            if(logger.isDebugEnabled()) {
              logger.debug("sip session already invalidated" + sipSessionKey);
            }
          }
          if(sipApplicationSession != null) {
            try {
              sipContext.enterSipApp(sipApplicationSession, null, false, true);
              if(logger.isDebugEnabled()) {
                logger.debug("sip app session " + sipApplicationSession.getKey() + " is valid ? :" + sipApplicationSession.isValidInternal());
                if(sipApplicationSession.isValidInternal()) {
                  logger.debug("Sip app session " + sipApplicationSession.getKey() + " is ready to be invalidated ? :" + sipApplicationSession.isReadyToInvalidate());
                }
              }
              if(sipApplicationSession.isValidInternal() && sipApplicationSession.isReadyToInvalidate()) {
                sipApplicationSession.tryToInvalidate();
              }
            } finally {
              sipContext.exitSipApp(sipApplicationSession, null);
            }
          }

        } finally {
          sipContext.exitSipContext(oldClassLoader);
        }
      }
    }
  }

  /*
   * (non-Javadoc)
   * @see gov.nist.javax.sip.SipListenerExt#processDialogTimeout(gov.nist.javax.sip.DialogTimeoutEvent)
   */
  public void processDialogTimeout(final DialogTimeoutEvent timeoutEvent) {
    final Dialog dialog = timeoutEvent.getDialog();
    if(logger.isDebugEnabled()) {
      logger.info("dialog timeout " + dialog + " reason => " + timeoutEvent.getReason());
    }
    if(timeoutEvent.getReason() == Reason.AckNotReceived) {
      final TransactionApplicationData tad = (TransactionApplicationData) dialog.getApplicationData();
      if(tad != null && tad.getSipServletMessage() != null) {
        // https://github.com/RestComm/sip-servlets/issues/107 guard against NPEon concurrent cleanup
        final SipServletMessageImpl sipServletMessage = tad.getSipServletMessage();
        final MobicentsSipSessionKey sipSessionKey = sipServletMessage.getSipSessionKey();
        final MobicentsSipSession sipSession = sipServletMessage.getSipSession();
        getAsynchronousExecutor().execute(new Runnable() {
          public void run() {
            if(logger.isDebugEnabled()) {
              logger.info("Running process dialog timeout " + dialog + " reason => " + timeoutEvent.getReason());
            }
            try {
              if(sipSession != null) {
                SipContext sipContext = findSipApplication(sipSessionKey.getApplicationName());
                //the context can be null if the server is being shutdown
                if(sipContext != null) {
                  MobicentsSipApplicationSession sipApplicationSession = sipSession.getSipApplicationSession();
                  try {
                    sipContext.enterSipApp(sipApplicationSession, sipSession, false, true);
                    checkForAckNotReceived(sipServletMessage);
                    checkForPrackNotReceived(sipServletMessage);
                  } finally {
                    sipContext.exitSipApp(sipApplicationSession, sipSession);
                  }
                  // Issue 1822 http://code.google.com/p/mobicents/issues/detail?id=1822
                  // don't delete the dialog so that the app can send the BYE even after the noAckReceived has been called
                  //								dialog.delete();
                  tryToInvalidateSession(sipSessionKey, false);
                }
              }
              tad.cleanUp();
              tad.cleanUpMessage();
              dialog.setApplicationData(null);
            } catch (Exception e) {
              logger.error("Problem handling dialog timeout", e);
            }
          }
        });
      } else{
        dialog.setApplicationData(null);
      }
    } else {
      dialog.setApplicationData(null);
    }
  }
  public SipConnector findSipConnector(String ipAddress, int port, String transport){
    MobicentsExtendedListeningPoint extendedListeningPoint = sipNetworkInterfaceManager.findMatchingListeningPoint(ipAddress, port, transport);
    if(extendedListeningPoint != null) {
      return extendedListeningPoint.getSipConnector();
    }
    return null;
  }

  /*
   * (non-Javadoc)
   * @see javax.sip.SipListener#processTimeout(javax.sip.TimeoutEvent)
   */
  public void processTimeout(final TimeoutEvent timeoutEvent) {
    Transaction eventTransaction = null;
    if(timeoutEvent.isServerTransaction()) {
      eventTransaction = timeoutEvent.getServerTransaction();
    } else {
      eventTransaction = timeoutEvent.getClientTransaction();
    }
    final Transaction transaction = eventTransaction;
    if(logger.isDebugEnabled()) {
      logger.debug("transaction " + transaction + " timed out => " + transaction.getRequest().toString());
    }

    final TransactionApplicationData tad = (TransactionApplicationData) transaction.getApplicationData();
    if(tad != null && tad.getSipServletMessage() != null) {
      // https://github.com/RestComm/sip-servlets/issues/107 guard against NPEon concurrent cleanup
      final SipServletMessageImpl sipServletMessage = tad.getSipServletMessage();
      final MobicentsSipSessionKey sipSessionKey = sipServletMessage.getSipSessionKey();
      final MobicentsSipSession sipSession = sipServletMessage.getSipSession();
      getAsynchronousExecutor().execute(new Runnable() {
        public void run() {
          try {
            if(logger.isDebugEnabled()) {
              logger.debug("transaction " + transaction + " timed out => " + transaction.getRequest().toString());
            }
            boolean appNotifiedOfPrackNotReceived = false;
            // session can be null if a message was sent outside of the container by the container itself during Initial request dispatching
            // but the external host doesn't send any response so we call out to the application only if the session is not null
            if(logger.isDebugEnabled()) {
              logger.debug("time out happened on sipSession " + sipSession);
            }
            if(sipSession != null) {
              SipContext sipContext = findSipApplication(sipSessionKey.getApplicationName());
              //the context can be null if the server is being shutdown
              if(sipContext != null) {
                MobicentsSipApplicationSession sipApplicationSession = sipSession.getSipApplicationSession();
                try {
                  sipContext.enterSipApp(sipApplicationSession, sipSession, false, true);
                  MobicentsB2BUAHelper b2buaHelperImpl = sipSession.getB2buaHelper();

                  if(b2buaHelperImpl != null && tad.getSipServletMessage() instanceof SipServletRequestImpl) {
                    b2buaHelperImpl.unlinkRequestInternal((SipServletRequestImpl)tad.getSipServletMessage(), false);
                  }
                  // naoki : Fix for Issue 1618 http://code.google.com/p/mobicents/issues/detail?id=1618 on Timeout don't do the 408 processing for Server Transactions
                  if(logger.isDebugEnabled()) {
                    logger.debug("time out happened on server tx ? " + timeoutEvent.isServerTransaction() + " and message " + sipServletMessage);
                  }
                  if(sipServletMessage instanceof SipServletRequestImpl && !timeoutEvent.isServerTransaction()) {
                    try {
                      ProxyBranchImpl proxyBranchImpl = tad.getProxyBranch();
                      if(proxyBranchImpl != null) {
                        ProxyImpl proxy = (ProxyImpl) proxyBranchImpl.getProxy();
                        if(proxy.getFinalBranchForSubsequentRequests() != null) {
                          tad.cleanUp();
                          transaction.setApplicationData(null);
                          return;
                        }
                      }
                      SipServletRequestImpl sipServletRequestImpl = (SipServletRequestImpl) sipServletMessage;
                      if(sipServletRequestImpl.visitNextHop()) {
                        return;
                      }
                      sipServletMessage.setTransaction(transaction);
                      SipServletResponseImpl response = (SipServletResponseImpl) sipServletRequestImpl.createResponse(408, null, false, true);
                      // Fix for Issue 1734
                      sipServletRequestImpl.setResponse(response);
                      MessageDispatcher.callServlet(response);
                      if(tad.getProxyBranch() != null) {
                        tad.getProxyBranch().setResponse(response);
                        tad.getProxyBranch().onResponse(response, response.getStatus());
                      }
                      sipSession.updateStateOnResponse(response, true);
                    } catch (Throwable t) {
                      logger.error("Failed to deliver 408 response on transaction timeout" + transaction, t);
                    }
                  }
                  // Guard only invite tx should check that, otherwise proxy might become null http://code.google.com/p/mobicents/issues/detail?id=2350
                  // Should check only Server Tx for ack or prack not received http://code.google.com/p/mobicents/issues/detail?id=2525
                  if(Request.INVITE.equals(sipServletMessage.getMethod()) && timeoutEvent.isServerTransaction()) {
                    checkForAckNotReceived(sipServletMessage);
                    appNotifiedOfPrackNotReceived = checkForPrackNotReceived(sipServletMessage);
                  }
                } finally {
                  sipSession.removeOngoingTransaction(transaction);
                  sipSession.setRequestsPending(0);
                  sipContext.exitSipApp(sipApplicationSession, sipSession);
                }
                // don't invalidate here because if the application sends a final response on the noPrack received
                // the ACK to this final response won't be able to get routed since the sip session would have been invalidated
                if(!appNotifiedOfPrackNotReceived) {
                  tryToInvalidateSession(sipSessionKey, false);
                }
              }
            }
            // don't clean up for the same reason we don't invalidate the sip session right above
            tad.cleanUp();
            transaction.setApplicationData(null);
          } catch (Exception e) {
            logger.error("Problem handling timeout", e);
          }
        }
      });
    }
  }

  private boolean checkForAckNotReceived(SipServletMessageImpl sipServletMessage) {
    //notifying SipErrorListener that no ACK has been received for a UAS only
    final MobicentsSipSession sipSession = sipServletMessage.getSipSession();
    final SipServletResponseImpl lastFinalResponse = (SipServletResponseImpl)
        ((SipServletRequestImpl)sipServletMessage).getLastFinalResponse();
    final MobicentsProxy proxy = sipSession.getProxy();
    if(logger.isDebugEnabled()) {
      logger.debug("checkForAckNotReceived : request " + sipServletMessage + " last Final Response " + lastFinalResponse);
    }
    boolean notifiedApplication = false;
    if(sipServletMessage instanceof SipServletRequestImpl && Request.INVITE.equals(sipServletMessage.getMethod()) &&
        proxy == null &&
        lastFinalResponse != null) {
      final SipContext sipContext = sipSession.getSipApplicationSession().getSipContext();
      final List<SipErrorListener> sipErrorListeners =
          sipContext.getListeners().getSipErrorListeners();

      final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
      try {
        sipContext.enterSipContext();

        final SipErrorEvent sipErrorEvent = new SipErrorEvent(
            (SipServletRequest)sipServletMessage,
            lastFinalResponse);

        for (SipErrorListener sipErrorListener : sipErrorListeners) {
          try {
            notifiedApplication = true;
            sipErrorListener.noAckReceived(sipErrorEvent);
          } catch (Throwable t) {
            logger.error("SipErrorListener threw exception", t);
          }
        }
      } finally {
        sipContext.exitSipContext(oldClassLoader);
      }
      final Dialog dialog = sipSession.getSessionCreatingDialog();
      if(!notifiedApplication && sipSession.getProxy() == null &&
          // Fix for http://code.google.com/p/mobicents/issues/detail?id=2525 BYE is being sent to a not yet established dialog
          dialog != null && dialog.getState() != null && !dialog.getState().equals(DialogState.TERMINATED)) {
        // Issue 1822 http://code.google.com/p/mobicents/issues/detail?id=1822
        // RFC 3261 Section 13.3.1.4 The INVITE is Accepted
        // "If the server retransmits the 2xx response for 64*T1 seconds without receiving an ACK,
        // the dialog is confirmed, but the session SHOULD be terminated.
        // This is accomplished with a BYE, as described in Section 15."
        SipServletRequest bye = sipSession.createRequest(Request.BYE);
        if(logger.isDebugEnabled()) {
          logger.debug("no applications called for ACK not received, sending BYE " + bye);
        }
        try {
          bye.send();
        } catch (IOException e) {
          logger.error("Couldn't send the BYE " + bye, e);
        }
      }
    }
    return notifiedApplication;
  }

  private boolean checkForPrackNotReceived(SipServletMessageImpl sipServletMessage) {
    //notifying SipErrorListener that no ACK has been received for a UAS only
    final MobicentsSipSession sipSession = sipServletMessage.getSipSession();
    SipServletResponseImpl lastInfoResponse = (SipServletResponseImpl)
        ((SipServletRequestImpl)sipServletMessage).getLastInformationalResponse();
    final MobicentsProxy proxy = sipSession.getProxy();
    if(logger.isDebugEnabled()) {
      logger.debug("checkForPrackNotReceived : last Informational Response " + lastInfoResponse);
    }
    boolean notifiedApplication = false;
    if(sipServletMessage instanceof SipServletRequestImpl && Request.INVITE.equals(sipServletMessage.getMethod()) &&
        proxy == null &&
        lastInfoResponse != null) {
      final SipContext sipContext = sipSession.getSipApplicationSession().getSipContext();
      final List<SipErrorListener> sipErrorListeners =
          sipContext.getListeners().getSipErrorListeners();

      final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
      try {
        sipContext.enterSipContext();

        final SipErrorEvent sipErrorEvent = new SipErrorEvent(
            (SipServletRequest)sipServletMessage,
            lastInfoResponse);
        for (SipErrorListener sipErrorListener : sipErrorListeners) {
          try {
            notifiedApplication = true;
            sipErrorListener.noPrackReceived(sipErrorEvent);
          } catch (Throwable t) {
            logger.error("SipErrorListener threw exception", t);
          }
        }
      } finally {
        sipContext.exitSipContext(oldClassLoader);
      }
    }
    return notifiedApplication;
  }

  /*
   * (non-Javadoc)
   * @see javax.sip.SipListener#processTransactionTerminated(javax.sip.TransactionTerminatedEvent)
   */
  public void processTransactionTerminated(final TransactionTerminatedEvent transactionTerminatedEvent) {
    Transaction eventTransaction = null;
    if(transactionTerminatedEvent.isServerTransaction()) {
      eventTransaction = transactionTerminatedEvent.getServerTransaction();
    } else {
      eventTransaction = transactionTerminatedEvent.getClientTransaction();
    }
    final Transaction transaction = eventTransaction;
    if(logger.isDebugEnabled()) {
      logger.info("transaction " + transaction + " terminated => " + transaction.getRequest());
    }

    final TransactionApplicationData tad = (TransactionApplicationData) transaction.getApplicationData();
    final String branchId = transaction.getBranchId();
    if(tad != null && tad.getSipServletMessage() != null) {
      // https://github.com/RestComm/sip-servlets/issues/107 guard against NPEon concurrent cleanup
      final SipServletMessageImpl sipServletMessageImpl = tad.getSipServletMessage();
      final MobicentsSipSessionKey sipSessionKey = sipServletMessageImpl.getSipSessionKey();
      final MobicentsSipSession sipSession = sipServletMessageImpl.getSipSession();
      getAsynchronousExecutor().execute(new Runnable() {
        public void run() {
          try {
            if(logger.isDebugEnabled()) {
              logger.info("transaction " + transaction + " terminated => " + sipServletMessageImpl);
            }

            MobicentsB2BUAHelper b2buaHelperImpl = null;
            if(sipSession != null) {
              b2buaHelperImpl = sipSession.getB2buaHelper();
            }
            if(sipSessionKey == null) {
              if(logger.isDebugEnabled()) {
                logger.debug("no sip session were returned for this key " + sipServletMessageImpl.getSipSessionKey() + " and message " + sipServletMessageImpl);
              }
            }
            ProxyBranchImpl proxyBranch = tad.getProxyBranch();
            if(proxyBranch != null) {
              proxyBranch.removeTransaction(branchId);
            }

            // Issue 1333 : B2buaHelper.getPendingMessages(linkedSession, UAMode.UAC) returns empty list
            // don't remove the transaction on terminated state for INVITE Tx because it won't be possible
            // to create the ACK on second leg for B2BUA apps
            if(sipSession != null) {
              boolean removeTx = true;
              if(b2buaHelperImpl != null && (transaction == null || transaction instanceof ClientTransaction)
                  && Request.INVITE.equals(sipServletMessageImpl.getMethod())) {
                removeTx = false;
              }
              SipContext sipContext = findSipApplication(sipSessionKey.getApplicationName());
              //the context can be null if the server is being shutdown
              if(sipContext != null) {
                MobicentsSipApplicationSession sipApplicationSession = sipSession.getSipApplicationSession();
                try {
                  sipContext.enterSipApp(sipApplicationSession, sipSession, false, true);

                  if(removeTx) {
                    if(b2buaHelperImpl != null && tad.getSipServletMessage() instanceof SipServletRequestImpl) {
                      b2buaHelperImpl.unlinkRequestInternal((SipServletRequestImpl)tad.getSipServletMessage(), false);
                    }
                    sipSession.removeOngoingTransaction(transaction);
                    // Issue 1468 : to handle forking, we shouldn't cleanup the app data since it is needed for the forked responses
                    boolean nullifyAppData = true;
                    if(((SipStackImpl)((SipProvider)transactionTerminatedEvent.getSource()).getSipStack()).getMaxForkTime() > 0 && Request.INVITE.equals(sipServletMessageImpl.getMethod())) {
                      nullifyAppData = false;
                    }
                    if(nullifyAppData) {
                      tad.cleanUp();
                      if(b2buaHelperImpl == null && tad.getSipServletMessage() instanceof SipServletRequestImpl) {
                        sipSession.cleanDialogInformation(false);
                      }
                      transaction.setApplicationData(null);
                    }
                  } else {
                    if(logger.isDebugEnabled()) {
                      logger.debug("Transaction " + transaction + " not removed from session " + sipSessionKey + " because the B2BUA might still need it to create the ACK");
                    }
                  }
                } finally {
                  sipContext.exitSipApp(sipApplicationSession, sipSession);
                }
              }

              // If it is a client transaction, do not kill the proxy session http://code.google.com/p/mobicents/issues/detail?id=1024
              tryToInvalidateSession(sipSessionKey, transactionTerminatedEvent.isServerTransaction());

            }
          } catch (Exception e) {
            logger.error("Problem handling transaction termination", e);
          }
        }
      });
    } else {
      if(logger.isDebugEnabled()) {
        logger.debug("TransactionApplicationData not available on the following request " + transaction.getRequest());
      }
      if(tad != null) {
        tad.cleanUp();
      }
      transaction.setApplicationData(null);
    }

  }

  public String getApplicationNameFromHash(String hash) {
    return mdToApplicationName.get(hash);
  }

  public String getHashFromApplicationName(String appName) {
    return applicationNameToMd.get(appName);
  }

  @Override
  public String getApplicationServerId() {
    return applicationServerId;
  }
  @Override
  public String getApplicationServerIdHash() {
    return applicationServerIdHash;
  }

  @Override
  public int getTagHashMaxLength() {
    return tagHashMaxLength;
  }

  @Override
  public CallIdHeader getCallId(
      MobicentsExtendedListeningPoint extendedListeningPoint, String callId) throws ParseException {
    String callIdString = callId;
    if(callIdString == null) {
      callIdString = extendedListeningPoint.getSipProvider().getNewCallId().getCallId();
    }
    if(callIdMaxLength > 0 && callIdString.length() > callIdMaxLength) {
      callIdString = callIdString.substring(0, callIdMaxLength);
    }
    return SipFactoryImpl.headerFactory.createCallIdHeader(callIdString);
  }

  /**
   * Check if the route is external
   * @param routeHeader the route to check
   * @return true if the route is external, false otherwise
   */
  public final boolean isRouteExternal(RouteHeader routeHeader) {
    if (routeHeader != null) {
      javax.sip.address.SipURI routeUri = (javax.sip.address.SipURI) routeHeader.getAddress().getURI();

      String routeTransport = routeUri.getTransportParam();
      if(routeTransport == null) {
        routeTransport = ListeningPoint.UDP;
      }
      return isExternal(routeUri.getHost(), routeUri.getPort(), routeTransport);
    }
    return true;
  }

  /**
   * Check if the via header is external
   * @param viaHeader the via header to check
   * @return true if the via header is external, false otherwise
   */
  public final boolean isViaHeaderExternal(ViaHeader viaHeader) {
    if (viaHeader != null) {
      return isExternal(viaHeader.getHost(), viaHeader.getPort(), viaHeader.getTransport());
    }
    return true;
  }

  /**
   * Check whether or not the triplet host, port and transport are corresponding to an interface
   * @param host can be hostname or ipaddress
   * @param port port number
   * @param transport transport used
   * @return true if the triplet host, port and transport are corresponding to an interface
   * false otherwise
   */
  public final boolean isExternal(String host, int port, String transport) {
    if(logger.isDebugEnabled()) {
      logger.debug("isExternal - host=" + host + ", port=" + port + ", transport=" + transport);
    }
    boolean isExternal = true;
    MobicentsExtendedListeningPoint listeningPoint = sipNetworkInterfaceManager.findMatchingListeningPoint(host, port, transport);
    if((hostNames.contains(host) || hostNames.contains(host+":" + port) || listeningPoint != null)) {
      if(logger.isDebugEnabled()) {
        logger.debug("hostNames.contains(" + host + ")=" +
            hostNames.contains(host) +
            "hostNames.contains(" + host + ":" + port + ")=" +
            hostNames.contains(host+":" + port) +
            " | listeningPoint found = " +
            listeningPoint);
      }
      isExternal = false;
    }
    if(logger.isDebugEnabled()) {
      logger.debug("the triplet host/port/transport : " +
          host + "/" +
          port + "/" +
          transport + " is external : " + isExternal);
    }
    return isExternal;
  }

  /**
   * @return the sipApplicationRouter
   */
  public SipApplicationRouter getSipApplicationRouter() {
    return sipApplicationRouter;
  }

  /**
   * @param sipApplicationRouter the sipApplicationRouter to set
   */
  public void setSipApplicationRouter(SipApplicationRouter sipApplicationRouter) {
    this.sipApplicationRouter = sipApplicationRouter;
  }

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#getSipNetworkInterfaceManager()
   */
  public SipNetworkInterfaceManager getSipNetworkInterfaceManager() {
    return this.sipNetworkInterfaceManager;
  }

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#getSipFactory()
   */
  public MobicentsSipFactory getSipFactory() {
    return sipFactoryImpl;
  }

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#getOutboundInterfaces()
   */
  public List<SipURI> getOutboundInterfaces() {
    return sipNetworkInterfaceManager.getOutboundInterfaces();
  }

  /**
   * set the outbound interfaces on all servlet context of applications deployed
   */
  private void resetOutboundInterfaces() {
    List<SipURI> outboundInterfaces = sipNetworkInterfaceManager.getOutboundInterfaces();
    for (SipContext sipContext : applicationDeployed.values()) {
      sipContext.getServletContext().setAttribute(javax.servlet.sip.SipServlet.OUTBOUND_INTERFACES,
          outboundInterfaces);
    }
  }

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#addHostName(java.lang.String)
   */
  public void addHostName(String hostName) {
    if(logger.isDebugEnabled()) {
      logger.debug(this);
      logger.debug("Adding hostname "+ hostName);
    }
    hostNames.add(hostName);
    if(dnsServerLocator != null) {
      dnsServerLocator.addLocalHostName(hostName);
    }
  }

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#findHostNames()
   */
  public Set<String> findHostNames() {
    return hostNames;
  }

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#removeHostName(java.lang.String)
   */
  public void removeHostName(String hostName) {
    if(logger.isDebugEnabled()) {
      logger.debug("Removing hostname "+ hostName);
    }
    hostNames.remove(hostName);
    if(dnsServerLocator != null) {
      dnsServerLocator.removeLocalHostName(hostName);
    }
  }

  /**
   *
   */
  public SipApplicationRouterInfo getNextInterestedApplication(
      MobicentsSipServletRequest sipServletRequest) {
    SipApplicationRoutingRegion routingRegion = null;
    Serializable stateInfo = null;
    if(sipServletRequest.getSipSession() != null) {
      routingRegion = sipServletRequest.getSipSession().getRegionInternal();
      stateInfo = sipServletRequest.getSipSession().getStateInfo();
    }
    final Request request = (Request) sipServletRequest.getMessage();

    sipServletRequest.setReadOnly(true);
    SipApplicationRouterInfo applicationRouterInfo = sipApplicationRouter.getNextApplication(
        sipServletRequest,
        routingRegion,
        sipServletRequest.getRoutingDirective(),
        null,
        stateInfo);
    sipServletRequest.setReadOnly(false);
    // 15.4.1 Procedure : point 2
    final SipRouteModifier sipRouteModifier = applicationRouterInfo.getRouteModifier();
    final String[] routes = applicationRouterInfo.getRoutes();
    try {
      // ROUTE modifier indicates that SipApplicationRouterInfo.getRoute() returns a valid route,
      // it is up to container to decide whether it is external or internal.
      if(SipRouteModifier.ROUTE.equals(sipRouteModifier)) {
        final Address routeAddress = SipFactoryImpl.addressFactory.createAddress(routes[0]);
        final RouteHeader applicationRouterInfoRouteHeader = SipFactoryImpl.headerFactory.createRouteHeader(routeAddress);
        if(isRouteExternal(applicationRouterInfoRouteHeader)) {
          // push all of the routes on the Route header stack of the request and
          // send the request externally
          for (int i = routes.length-1 ; i >= 0; i--) {
            RouteHeader routeHeader = (RouteHeader) SipFactoryImpl.headerFactory.createHeader(RouteHeader.NAME, routes[i]);
            URI routeURI = routeHeader.getAddress().getURI();
            if(routeURI.isSipURI()) {
              ((javax.sip.address.SipURI)routeURI).setLrParam();
            }
            request.addHeader(routeHeader);
          }
        }
      } else if (SipRouteModifier.ROUTE_BACK.equals(sipRouteModifier)) {
        // Push container Route, pick up the first outbound interface
        final SipURI sipURI = getOutboundInterfaces().get(0);
        sipURI.setParameter("modifier", "route_back");
        Header routeHeader = SipFactoryImpl.headerFactory.createHeader(RouteHeader.NAME, sipURI.toString());
        request.addHeader(routeHeader);
        // push all of the routes on the Route header stack of the request and
        // send the request externally
        for (int i = routes.length-1 ; i >= 0; i--) {
          routeHeader = SipFactoryImpl.headerFactory.createHeader(RouteHeader.NAME, routes[i]);
          request.addHeader(routeHeader);
        }
      }
    } catch (ParseException e) {
      logger.error("Impossible to parse the route returned by the application router " +
          "into a compliant address",e);
    }
    return applicationRouterInfo;
  }

  public ThreadPoolExecutor getAsynchronousExecutor() {
    return asynchronousExecutor;
  }

  public ScheduledThreadPoolExecutor getAsynchronousScheduledExecutor() {
    return asynchronousScheduledThreadPoolExecutor;
  }

  /**
   * Serialize the state info in memory and deserialize it and return the new object.
   * Since there is no clone method this is the only way to get the same object with a new reference
   * @param stateInfo the state info to serialize
   * @return the state info serialized and deserialized
   */
//	private Serializable serializeStateInfo(Serializable stateInfo) {
//		ByteArrayOutputStream baos = null;
//		ObjectOutputStream out = null;
//		ByteArrayInputStream bais = null;
//		ObjectInputStream in = null;
//
//		try{
//			baos = new ByteArrayOutputStream();
//			out = new ObjectOutputStream(baos);
//			out.writeObject(stateInfo);
//			bais = new ByteArrayInputStream(baos.toByteArray());
//			in =new ObjectInputStream(bais);
//			return (Serializable)in.readObject();
//		} catch (IOException e) {
//			logger.error("Impossible to serialize the state info", e);
//			return stateInfo;
//		} catch (ClassNotFoundException e) {
//			logger.error("Impossible to serialize the state info", e);
//			return stateInfo;
//		} finally {
//			try {
//				if(out != null) {
//					out.close();
//				}
//				if(in != null) {
//					in.close();
//				}
//				if(baos != null) {
//					baos.close();
//				}
//				if(bais != null) {
//					bais.close();
//				}
//			} catch (IOException e) {
//				logger.error("Impossible to close the streams after serializing state info", e);
//			}
//		}
//	}

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#findSipApplications()
   */
  public Iterator<SipContext> findSipApplications() {
    return applicationDeployed.values().iterator();
  }

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#findSipApplication(java.lang.String)
   */
  public SipContext findSipApplication(String applicationName) {
    return applicationDeployed.get(applicationName);
  }

  public DNSResolver getDNSResolver() {
    return dnsResolver;
  }

  public DNSServerLocator getDNSServerLocator() {
    return dnsServerLocator;
  }

  public void setDNSServerLocator(DNSServerLocator dnsServerLocator) {
    this.dnsServerLocator = dnsServerLocator;
  }

  public int getDNSTimeout() {
    return dnsTimeout;
  }

  public void setDNSTimeout(int dnsTimeout) {
    this.dnsTimeout = dnsTimeout;
    if(logger.isInfoEnabled()) {
      logger.info("DNSServerLocator will be using timeout of " + dnsTimeout + " seconds ");
    }
    dnsServerLocator.getDnsLookupPerformer().setDNSTimeout(dnsTimeout);
  }


  // -------------------- JMX and Registration  --------------------
  protected String domain;
  protected ObjectName oname;
  protected MBeanServer mserver;

  public ObjectName getObjectName() {
    return oname;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  /*
   * (non-Javadoc)
   * @see javax.management.MBeanRegistration#postDeregister()
   */
  public void postDeregister() {}

  /*
   * (non-Javadoc)
   * @see javax.management.MBeanRegistration#postRegister(java.lang.Boolean)
   */
  public void postRegister(Boolean registrationDone) {}

  /*
   * (non-Javadoc)
   * @see javax.management.MBeanRegistration#preDeregister()
   */
  public void preDeregister() throws Exception {}

  /*
   * (non-Javadoc)
   * @see javax.management.MBeanRegistration#preRegister(javax.management.MBeanServer, javax.management.ObjectName)
   */
  public ObjectName preRegister(MBeanServer server, ObjectName name)
      throws Exception {
    oname=name;
    mserver=server;
    domain=name.getDomain();
    return name;
  }

  /* Exposed methods for the management console. Some of these duplicate existing methods, but
   * with JMX friendly types.
   */
  public String[] findInstalledSipApplications() {
    Iterator<SipContext> apps = findSipApplications();
    ArrayList<String> appList = new ArrayList<String>();
    while(apps.hasNext()){
      SipContext ctx = apps.next();
      appList.add(ctx.getApplicationName());
    }
    String[] ret = new String[appList.size()];
    for(int q=0; q<appList.size(); q++) ret[q] = appList.get(q);
    return ret;
  }

  public Object retrieveApplicationRouterConfiguration() {
    if(this.sipApplicationRouter instanceof ManageableApplicationRouter) {
      ManageableApplicationRouter router = (ManageableApplicationRouter) this.sipApplicationRouter;
      return router.getCurrentConfiguration();
    } else {
      throw new RuntimeException("This application router is not manageable");
    }
  }

  public Map<String, List<? extends SipApplicationRouterInfo>> getApplicationRouterConfiguration() {
    if(this.sipApplicationRouter instanceof ManageableApplicationRouter) {
      ManageableApplicationRouter router = (ManageableApplicationRouter) this.sipApplicationRouter;
      return router.getConfiguration();
    } else {
      throw new RuntimeException("This application router is not manageable");
    }
  }

  public void updateApplicationRouterConfiguration(Object configuration) {
    if(this.sipApplicationRouter instanceof ManageableApplicationRouter) {
      ManageableApplicationRouter router = (ManageableApplicationRouter) this.sipApplicationRouter;
      router.configure(configuration);
    } else {
      throw new RuntimeException("This application router is not manageable");
    }
  }

  public Serializable retrieveApplicationRouterConfigurationString() {
    if(this.sipApplicationRouter instanceof ManageableApplicationRouter) {
      ManageableApplicationRouter router = (ManageableApplicationRouter) this.sipApplicationRouter;
      return (Serializable) router.getCurrentConfiguration();
    } else {
      throw new RuntimeException("This application router is not manageable");
    }
  }

  public void updateApplicationRouterConfiguration(Serializable configuration) {
    if(this.sipApplicationRouter instanceof ManageableApplicationRouter) {
      ManageableApplicationRouter router = (ManageableApplicationRouter) this.sipApplicationRouter;
      router.configure(configuration);
    } else {
      throw new RuntimeException("This application router is not manageable");
    }
  }

  public ConcurrencyControlMode getConcurrencyControlMode() {
    return concurrencyControlMode;
  }

  public void setConcurrencyControlMode(ConcurrencyControlMode concurrencyControlMode) {
    this.concurrencyControlMode = concurrencyControlMode;
    if(logger.isInfoEnabled()) {
      logger.info("Container wide Concurrency Control set to " + concurrencyControlMode);
    }
  }

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(int queueSize) {
    this.queueSize = queueSize;
    if(logger.isInfoEnabled()) {
      logger.info("Queue Size set to " + queueSize);
    }
  }

  public void setConcurrencyControlModeByName(String concurrencyControlMode) {
    this.concurrencyControlMode = ConcurrencyControlMode.valueOf(concurrencyControlMode);
    if(logger.isInfoEnabled()) {
      logger.info("Container wide Concurrency Control set to " + concurrencyControlMode);
    }
  }

  @Override
  public String getConcurrencyControlModeByName() {
    return concurrencyControlMode.toString();
  }

  /**
   * @return the requestsProcessed
   */
  public long getRequestsProcessed() {
    return requestsProcessed.get();
  }

  /**
   * @return the requestsProcessedByMethod
   */
  public Map<String, AtomicLong> getRequestsProcessedByMethod() {
    return requestsProcessedByMethod;
  }

  /**
   * @return the responsesProcessedByStatusCode
   */
  public Map<String, AtomicLong> getResponsesProcessedByStatusCode() {
    return responsesProcessedByStatusCode;
  }

  /**
   * @return the requestsProcessed
   */
  public long getRequestsProcessedByMethod(String method) {
    AtomicLong requestsProcessed = requestsProcessedByMethod.get(method);
    if(requestsProcessed != null) {
      return requestsProcessed.get();
    }
    return 0;
  }

  public long getResponsesProcessedByStatusCode(String statusCode) {
    AtomicLong responsesProcessed = responsesProcessedByStatusCode.get(statusCode);
    if(responsesProcessed != null) {
      return responsesProcessed.get();
    }
    return 0;
  }

  /**
   * @return the requestsProcessed
   */
  public long getResponsesProcessed() {
    return responsesProcessed.get();
  }

  /**
   * @return the requestsProcessed
   */
  public long getRequestsSent() {
    return requestsSent.get();
  }

  /**
   * @return the requestsProcessedByMethod
   */
  public Map<String, AtomicLong> getRequestsSentByMethod() {
    return requestsSentByMethod;
  }

  /**
   * @return the responsesProcessedByStatusCode
   */
  public Map<String, AtomicLong> getResponsesSentByStatusCode() {
    return responsesSentByStatusCode;
  }

  /**
   * @return the requestsProcessed
   */
  public long getRequestsSentByMethod(String method) {
    AtomicLong requestsSent = requestsSentByMethod.get(method);
    if(requestsSent != null) {
      return requestsSent.get();
    }
    return 0;
  }

  public long getResponsesSentByStatusCode(String statusCode) {
    AtomicLong responsesSent = responsesSentByStatusCode.get(statusCode);
    if(responsesSent != null) {
      return responsesSent.get();
    }
    return 0;
  }

  /**
   * @return the requestsProcessed
   */
  public long getResponsesSent() {
    return responsesSent.get();
  }

  static final String INTERVAL_ATT = "Interval";
  class SetCongAction implements DispatcherFSM.Action {
    @Override
    public void execute(DispatcherFSM.Context ctx) {
      if (congestionControlTimerFuture != null) {
        congestionControlTimerFuture.cancel(false);
      }
      if (congestionControlCheckingInterval > 0) {
        congestionControlTimerFuture = asynchronousScheduledThreadPoolExecutor.scheduleWithFixedDelay(congestionControlTimerTask, congestionControlCheckingInterval, congestionControlCheckingInterval, TimeUnit.MILLISECONDS);
        if (logger.isInfoEnabled()) {
          logger.info("Congestion control background task modified to check every " + congestionControlCheckingInterval + " milliseconds.");
        }
      } else if (logger.isInfoEnabled()) {
        logger.info("No Congestion control background task started since the checking interval is equals to " + congestionControlCheckingInterval + " milliseconds.");
      }
    }

  }
  /**
   * @param congestionControlCheckingInterval the congestionControlCheckingInterval to set
   */
  public void setCongestionControlCheckingInterval(
      long congestionControlCheckingInterval) {
    //save value anyway,during start actual scheduling will take place
    this.congestionControlCheckingInterval = congestionControlCheckingInterval;
    //fire event so congestion reschedule is only invoked after start.
    DispatcherFSM.Event congEvent = fsm.new Event(DispatcherFSM.EventType.SET_CONGESTION);
    congEvent.data.put(INTERVAL_ATT, new Long(congestionControlCheckingInterval));
    fsm.fireEvent(congEvent);
  }

  /**
   * @return the congestionControlCheckingInterval
   */
  public long getCongestionControlCheckingInterval() {
    return congestionControlCheckingInterval;
  }

  /**
   * @param congestionControlPolicy the congestionControlPolicy to set
   */
  public void setCongestionControlPolicy(CongestionControlPolicy congestionControlPolicy) {
    this.congestionControlPolicy = congestionControlPolicy;
    if(logger.isInfoEnabled()) {
      logger.info("Congestion Control policy set to " + this.congestionControlPolicy.toString());
    }
  }


  public void setCongestionControlPolicyByName(String congestionControlPolicy) {
    this.congestionControlPolicy = CongestionControlPolicy.valueOf(congestionControlPolicy);
    if(logger.isInfoEnabled()) {
      logger.info("Congestion Control policy set to " + this.congestionControlPolicy.toString());
    }
  }

  /**
   * @return the congestionControlPolicy
   */
  public String getCongestionControlPolicyByName() {
    return congestionControlPolicy.toString();
  }

  /**
   * @return the congestionControlPolicy
   */
  public CongestionControlPolicy getCongestionControlPolicy() {
    return congestionControlPolicy;
  }

  /**
   * @param memoryThreshold the memoryThreshold to set
   */
  public void setMemoryThreshold(int memoryThreshold) {
    this.memoryThreshold = memoryThreshold;
    if(logger.isInfoEnabled()) {
      logger.info("Memory threshold set to " + this.memoryThreshold +"%");
    }
  }

  /**
   * @return the memoryThreshold
   */
  public int getMemoryThreshold() {
    return memoryThreshold;
  }

  /**
   * @return the numberOfMessagesInQueue
   */
  public int getNumberOfMessagesInQueue() {
    return numberOfMessagesInQueue;
  }

  /**
   * @return the percentageOfMemoryUsed
   */
  public double getPercentageOfMemoryUsed() {
    return percentageOfMemoryUsed;
  }

  /**
   * @param bypassRequestExecutor the bypassRequestExecutor to set
   */
  public void setBypassRequestExecutor(boolean bypassRequestExecutor) {
    this.bypassRequestExecutor = bypassRequestExecutor;
    if(logger.isInfoEnabled()) {
      logger.info("Bypass Request Executor enabled ?" + this.bypassRequestExecutor);
    }
  }

  /**
   * @return the bypassRequestExecutor
   */
  public boolean isBypassRequestExecutor() {
    return bypassRequestExecutor;
  }

  /**
   * @param bypassResponseExecutor the bypassResponseExecutor to set
   */
  public void setBypassResponseExecutor(boolean bypassResponseExecutor) {
    this.bypassResponseExecutor = bypassResponseExecutor;
    if(logger.isInfoEnabled()) {
      logger.info("Bypass Response Executor enabled ?" + this.bypassResponseExecutor);
    }
  }

  /**
   * @return the bypassResponseExecutor
   */
  public boolean isBypassResponseExecutor() {
    return bypassResponseExecutor;
  }

  /**
   * @param baseTimerInterval the baseTimerInterval to set
   */
  public void setBaseTimerInterval(int baseTimerInterval) {
    if(baseTimerInterval < 1) {
      throw new IllegalArgumentException("It's forbidden to set the Base Timer Interval to a non positive value");
    }
    this.baseTimerInterval = baseTimerInterval;
    if(logger.isInfoEnabled()) {
      logger.info("SIP Base Timer Interval set to " + this.baseTimerInterval +"ms");
    }
  }

  /**
   * @return the baseTimerInterval
   */
  public int getBaseTimerInterval() {
    return baseTimerInterval;
  }

  /**
   * @param t2Interval the t2Interval to set
   */
  public void setT2Interval(int t2Interval) {
    if(t2Interval < 1) {
      throw new IllegalArgumentException("It's forbidden to set the SIP Timer T2 Interval to a non positive value");
    }
    this.t2Interval = t2Interval;
    if(logger.isInfoEnabled()) {
      logger.info("SIP Timer T2 Interval set to " + this.t2Interval +"ms");
    }
  }


  /**
   * @return the t2Interval
   */
  public int getT2Interval() {
    return t2Interval;
  }


  /**
   * @param t4Interval the t4Interval to set
   */
  public void setT4Interval(int t4Interval) {
    if(t4Interval < 1) {
      throw new IllegalArgumentException("It's forbidden to set the SIP Timer T4 Interval to a non positive value");
    }
    this.t4Interval = t4Interval;
    if(logger.isInfoEnabled()) {
      logger.info("SIP Timer T4 Interval set to " + this.t4Interval +"ms");
    }
  }


  /**
   * @return the t4Interval
   */
  public int getT4Interval() {
    return t4Interval;
  }


  /**
   * @param timerDInterval the timerDInterval to set
   */
  public void setTimerDInterval(int timerDInterval) {
    if(timerDInterval < 1) {
      throw new IllegalArgumentException("It's forbidden to set the SIP Timer TD Interval to a non positive value");
    }
    if(timerDInterval < 32000) {
      throw new IllegalArgumentException("It's forbidden to set the SIP Timer TD Interval to a value lower than 32s");
    }
    this.timerDInterval = timerDInterval;
    if(logger.isInfoEnabled()) {
      logger.info("SIP Timer D Interval set to " + this.timerDInterval +"ms");
    }
  }


  /**
   * @return the timerDInterval
   */
  public int getTimerDInterval() {
    return timerDInterval;
  }

  public String[] getExtensionsSupported() {
    return EXTENSIONS_SUPPORTED;
  }

  public String[] getRfcSupported() {
    return RFC_SUPPORTED;
  }

  @Override
  public void loadBalancerAdded(SipLoadBalancer sipLoadBalancer) {
    sipLoadBalancers.add(sipLoadBalancer);
    if(sipFactoryImpl.getLoadBalancerToUse() == null) {
      sipFactoryImpl.setLoadBalancerToUse(sipLoadBalancer);
    }
  }

  @Override
  public void loadBalancerRemoved(SipLoadBalancer sipLoadBalancer) {
    sipLoadBalancers.remove(sipLoadBalancer);
    if(sipFactoryImpl.getLoadBalancerToUse() != null &&
        sipFactoryImpl.getLoadBalancerToUse().equals(sipLoadBalancer)) {
      if(sipLoadBalancers.size() > 0) {
        sipFactoryImpl.setLoadBalancerToUse(sipLoadBalancers.iterator().next());
      } else {
        sipFactoryImpl.setLoadBalancerToUse(null);
      }
    }
  }

  // https://github.com/RestComm/sip-servlets/issues/172
  @Override
  public void pingingloadBalancer(SipLoadBalancer balancerDescription) {
    SipConnector[] sipConnectors = sipService.findSipConnectors();
    for (SipConnector sipConnector : sipConnectors) {
      if(logger.isDebugEnabled()) {
        logger.debug("Comparing Balancer Address " + balancerDescription.getAddress().getHostAddress() +
            " to sipconnector balancer address " + sipConnector.getLoadBalancerAddress());
      }
      if(balancerDescription.getAddress().getHostAddress().equals(sipConnector.getLoadBalancerAddress())
          && sipConnector.getLoadBalancerCustomInformation() != null
          && !sipConnector.getLoadBalancerCustomInformation().isEmpty()) {
        balancerDescription.setCustomInfo(sipConnector.getLoadBalancerCustomInformation());
      }
    }
  }

  @Override
  public void pingedloadBalancer(SipLoadBalancer balancerDescription) {
    // Nothing to do here
  }

  /**
   * @param info
   */
  public void sendSwitchoverInstruction(String fromJvmRoute, String toJvmRoute) {
    if(logger.isDebugEnabled()) {
      logger.debug("switching over from " + fromJvmRoute + " to " + toJvmRoute);
    }
    if(fromJvmRoute == null || toJvmRoute == null) {
      return;
    }
    for(SipLoadBalancer  sipLoadBalancer : sipLoadBalancers) {
      sipLoadBalancer.switchover(fromJvmRoute, toJvmRoute);
    }
  }

  /*
   * (non-Javadoc)
   * @see org.mobicents.servlet.sip.core.SipApplicationDispatcher#setGracefulShutdown(boolean)
   */
  public void setGracefulShutdown(boolean shuttingDownGracefully) {
    if(logger.isDebugEnabled()) {
      logger.debug("sending graceful shutdown to Load Balancers");
    }
//		for(SipLoadBalancer  sipLoadBalancer : sipLoadBalancers) {
//			sipLoadBalancer.setGracefulShutdown(shuttingDownGracefully);
//		}
  }

  /**
   * @param gatherStatistics the skipStatistics to set
   */
  public void setGatherStatistics(boolean gatherStatistics) {
    this.gatherStatistics = gatherStatistics;
    if(logger.isInfoEnabled()) {
      logger.info("Gathering Statistics set to " + gatherStatistics);
    }
  }

  /**
   * @return the skipStatistics
   */
  public boolean isGatherStatistics() {
    return gatherStatistics;
  }

  /**
   * PRESENT TO ACCOMODATE JOPR. NEED TO FILE A BUG ON THIS
   * @return the skipStatistics
   */
  public boolean getGatherStatistics() {
    return gatherStatistics;
  }

  /**
   * @param backToNormalPercentageOfMemoryUsed the backToNormalPercentageOfMemoryUsed to set
   */
  public void setBackToNormalMemoryThreshold(
      int backToNormalMemoryThreshold) {
    this.backToNormalMemoryThreshold = backToNormalMemoryThreshold;
    if(logger.isInfoEnabled()) {
      logger.info("Back To Normal Memory threshold set to " + backToNormalMemoryThreshold +"%");
    }
  }

  /**
   * @return the backToNormalPercentageOfMemoryUsed
   */
  public int getBackToNormalMemoryThreshold() {
    return backToNormalMemoryThreshold;
  }

  /**
   * @param backToNormalQueueSize the backToNormalQueueSize to set
   */
  public void setBackToNormalQueueSize(int backToNormalQueueSize) {
    this.backToNormalQueueSize = backToNormalQueueSize;
    if(logger.isInfoEnabled()) {
      logger.info("Back To Normal Queue Size set to " + backToNormalQueueSize);
    }
  }

  /**
   * @return the backToNormalQueueSize
   */
  public int getBackToNormalQueueSize() {
    return backToNormalQueueSize;
  }

  public SipStack getSipStack() {
    return sipStack;
  }

  public void setSipStack(SipStack sipStack) {
    this.sipStack = sipStack;
  }

  protected void startSipStack() throws SipException {
    // stopping the sip stack
    if(sipStack != null) {
      sipStack.start();
      if(logger.isDebugEnabled()) {
        logger.debug("SIP stack started");
      }
    }
  }

  protected void stopSipStack() {
    // stopping the sip stack
    if(sipStack != null) {
      sipStack.stop();
      sipStack = null;
      if(logger.isDebugEnabled()) {
        logger.debug("SIP stack stopped");
      }
    }
  }

  public String getVersion() {
    return Version.getVersion();
  }

  @Override
  public SipService getSipService() {
    return sipService;
  }

  @Override
  public void setSipService(SipService sipService) {
    this.sipService = sipService;
  }
}