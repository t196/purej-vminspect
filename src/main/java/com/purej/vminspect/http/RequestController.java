// Copyright (c), 2013, adopus consulting GmbH Switzerland, all rights reserved.
package com.purej.vminspect.http;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.purej.vminspect.data.MBeanAttribute;
import com.purej.vminspect.data.MBeanData;
import com.purej.vminspect.data.MBeanOperation;
import com.purej.vminspect.data.MBeanUtils;
import com.purej.vminspect.data.SystemData;
import com.purej.vminspect.data.ThreadData;
import com.purej.vminspect.data.statistics.Range;
import com.purej.vminspect.data.statistics.Statistics;
import com.purej.vminspect.data.statistics.StatisticsCollector;
import com.purej.vminspect.html.AbstractHtmlView;
import com.purej.vminspect.html.ConfirmState;
import com.purej.vminspect.html.HtmlPageView;
import com.purej.vminspect.html.MBeansDetailView;
import com.purej.vminspect.html.MBeansInvokeAttributeView;
import com.purej.vminspect.html.MBeansInvokeOperationView;
import com.purej.vminspect.html.MBeansMainView;
import com.purej.vminspect.html.StatisticsDetailView;
import com.purej.vminspect.html.StatisticsMainView;
import com.purej.vminspect.html.SystemMainView;
import com.purej.vminspect.html.ThreadsDumpView;
import com.purej.vminspect.html.ThreadsMainView;
import com.purej.vminspect.util.Message;
import com.purej.vminspect.util.Message.MessageType;
import com.purej.vminspect.util.Utils;

/**
 * This is the controller that dispatches each request depending on the request-parameters to the correct view.
 *
 * @author Stefan Mueller
 */
public class RequestController {
  private static final Set<String> NO_REFRESH_PARAMS = new HashSet<String>();
  static {
    NO_REFRESH_PARAMS.add(RequestParams.MBEAN_DOMAIN_FILTER);
    NO_REFRESH_PARAMS.add(RequestParams.MBEAN_TYPE_FILTER);

    NO_REFRESH_PARAMS.add(RequestParams.MBEAN_ATTRIBUTE_INVOKE);
    NO_REFRESH_PARAMS.add(RequestParams.MBEAN_ATTRIBUTE_CANCEL);
    NO_REFRESH_PARAMS.add(RequestParams.MBEAN_ATTRIBUTE_NAME);
    NO_REFRESH_PARAMS.add(RequestParams.MBEAN_ATTRIBUTE_VALUE);

    NO_REFRESH_PARAMS.add(RequestParams.MBEAN_OPERATION_INVOKE);
    NO_REFRESH_PARAMS.add(RequestParams.MBEAN_OPERATION_CANCEL);
    NO_REFRESH_PARAMS.add(RequestParams.MBEAN_OPERATION_IDX);
  }

  private final StatisticsCollector _collector;

  /**
   * Creates a new instance of this class.
   *
   * @param collector the collector
   */
  public RequestController(StatisticsCollector collector) {
    super();
    _collector = collector;
  }

  /**
   * Processes the given request and returns the in-memory response.
   *
   * @param httpRequest the request to be processed
   * @param mbeanAccessControl handles fine-grained access control for MBeans, mandatory only for each requests concerning a single MBean
   * @return the in-memory response
   * @throws IOException if an exception occurred
   */
  public HttpResponse process(HttpRequest httpRequest, MBeanAccessControl mbeanAccessControl) throws IOException {
    // 1.) Check if static resource (eg. png/css/etc.)
    String resource = httpRequest.getParameter(RequestParams.RESOURCE);
    if (resource != null) {
      return new HttpResourceResponse(resource);
    }
    else {
      // 2.) Check if graph (eg. statistics graphic):
      String statsGraphName = httpRequest.getParameter(RequestParams.STATS_GRAPH);
      if (statsGraphName != null) {
        return doStatsGraph(httpRequest, statsGraphName);
      }
      // 3.) Check if thread dump:
      String page = httpRequest.getParameter(RequestParams.PAGE);
      if ("threadsDump".equals(page)) {
        return doThreadDump();
      }
      // 4.) All other output are HTML based:
      else {
        return doHtml(httpRequest, page, mbeanAccessControl);
      }
    }
  }

  private HttpResponse doStatsGraph(HttpRequest httpRequest, String graphName) throws IOException {
    HttpPngResponse response = new HttpPngResponse(graphName);
    Range range = CookieManager.getRange(httpRequest, response);
    int width = Math.min(Integer.parseInt(httpRequest.getParameter(RequestParams.STATS_WIDTH)), 1600);
    int height = Math.min(Integer.parseInt(httpRequest.getParameter(RequestParams.STATS_HEIGHT)), 1600);
    Statistics stats = _collector.getStatistics(graphName);
    response.setImg(stats.createGraph(range, width, height));
    return response;
  }

  private static HttpResponse doThreadDump() throws IOException {
    HttpTextResponse response = new HttpTextResponse("text/plain; charset=utf-8");
    new ThreadsDumpView(response.getOutput(), ThreadData.getAllThreads()).render();
    return response;
  }

  private HttpResponse doHtml(HttpRequest request, String page, MBeanAccessControl mbeanAccessControl) throws IOException {
    long start = System.currentTimeMillis();
    HttpTextResponse response = new HttpTextResponse("text/html; charset=utf-8");

    // Create the content view:
    AbstractHtmlView view;
    if ("mbeans".equals(page)) {
      view = handleMBeansView(request, response, mbeanAccessControl);
    }
    else if ("threads".equals(page)) {
      view = new ThreadsMainView(response.getOutput(), ThreadData.getAllThreads());
    }
    else if ("statistics".equals(page)) {
      Range range = CookieManager.getRange(request, response);
      String statsName = request.getParameter(RequestParams.STATS_DETAIL);
      if (statsName != null) {
        String statsWidth = request.getParameter(RequestParams.STATS_WIDTH);
        String statsHeight = request.getParameter(RequestParams.STATS_HEIGHT);
        view = new StatisticsDetailView(response.getOutput(), range, statsName, Integer.parseInt(statsWidth), Integer.parseInt(statsHeight));
      }
      else {
        view = new StatisticsMainView(response.getOutput(), _collector, range);
      }
    }
    else {
      // For all other cases (eg. page=system or page missing) we show system page:
      view = new SystemMainView(response.getOutput(), new SystemData());
    }

    // Create the page template and render:
    HtmlPageView html = new HtmlPageView(response.getOutput(), getRefreshParameters(request), start, view);
    html.render();
    return response;
  }

  private static AbstractHtmlView handleMBeansView(HttpRequest request, HttpTextResponse response, MBeanAccessControl mbeanAccessControl)
      throws IOException {
    String mbServerIdx = request.getParameter(RequestParams.MBEAN_SRV_IDX);
    String mbName = request.getParameter(RequestParams.MBEAN_NAME);
    if (mbServerIdx != null && mbName != null) {
      // MBean specified:
      MBeanData mbean = MBeanUtils.getMBean(Integer.parseInt(mbServerIdx), mbName);
      String mbAtrName = request.getParameter(RequestParams.MBEAN_ATTRIBUTE_NAME);
      String mbOpIdx = request.getParameter(RequestParams.MBEAN_OPERATION_IDX);
      if (mbAtrName != null) {
        return handleMBeansAttributeView(request, response, mbean, mbAtrName, mbeanAccessControl);
      }
      else if (mbOpIdx != null) {
        return handleMBeansOperationView(request, response, mbean, mbOpIdx, mbeanAccessControl);
      }
      else {
        // Show MBean page:
        return new MBeansDetailView(response.getOutput(), mbean, null, mbeanAccessControl);
      }
    }
    else {
      // Show the main view:
      String domainFilter = CookieManager.getDomainFilter(request, response);
      String typeFilter = CookieManager.getTypeFilter(request, response);
      return new MBeansMainView(response.getOutput(), domainFilter, typeFilter, MBeanUtils.getMBeanNames());
    }
  }

  private static AbstractHtmlView handleMBeansAttributeView(HttpRequest request, HttpTextResponse response, MBeanData mbean, String mbAtrName,
      MBeanAccessControl mbeanAccessControl) throws IOException {
    // MBean Attribute specified:
    MBeanAttribute attribute = mbean.getAttribute(mbAtrName);
    String newValue = request.getParameter(RequestParams.MBEAN_ATTRIBUTE_VALUE);
    if (request.getParameter(RequestParams.MBEAN_ATTRIBUTE_INVOKE) != null) {
      if (!mbeanAccessControl.isChangeAllowed(mbean, attribute)) {
        throw new UnsupportedOperationException("Not allowed to edit the attribute according to MBeanAccessControl!");
      }

      Message msg;
      MBeanData reloaded = mbean;
      try {
        // Invoke the attribute:
        Object result = MBeanUtils.invokeAttribute(mbean.getName(), attribute, newValue);
        mbeanAccessControl.attributeChanged(mbean, attribute, result);

        // Reload state & show MBean page:
        reloaded = MBeanUtils.getMBean(mbean.getName());
        msg = new Message("Attribute <b>" + attribute.getName() + "</b> successfully set to value <b>"
            + Utils.htmlEncode(result != null ? result.toString() : "null") + "</b>.", MessageType.OK);
      }
      catch (Exception e) {
        mbeanAccessControl.attributeChangeFailed(mbean, attribute, e);
        msg = new Message(Utils.getHtmlExceptionInfo(e), MessageType.ERROR);
      }
      return new MBeansDetailView(response.getOutput(), reloaded, msg, mbeanAccessControl);
    }
    else if (request.getParameter(RequestParams.MBEAN_ATTRIBUTE_INVOKE_CONFIRM) != null) {
      // Show edit attribute confirmation:
      return new MBeansInvokeAttributeView(response.getOutput(), mbean, attribute, ConfirmState.NOW, newValue);
    }
    else if (request.getParameter(RequestParams.MBEAN_ATTRIBUTE_CANCEL) != null) {
      // Show MBean page:
      Message warnMsg = new Message("Canceled, attribute <b>" + attribute.getName() + "</b> not set!", MessageType.WARN);
      return new MBeansDetailView(response.getOutput(), mbean, warnMsg, mbeanAccessControl);
    }
    else {
      // Show page to edit the attribute:
      ConfirmState confirmState = mbeanAccessControl.needsChangeConfirmation(mbean, attribute) ? ConfirmState.NEXT : ConfirmState.OFF;
      return new MBeansInvokeAttributeView(response.getOutput(), mbean, attribute, confirmState, null);
    }
  }

  private static AbstractHtmlView handleMBeansOperationView(HttpRequest request, HttpTextResponse response, MBeanData mbean, String mbOpIdx,
      MBeanAccessControl mbeanAccessControl) throws IOException {
    // MBean Operation specified:
    int opIdx = Integer.parseInt(mbOpIdx);
    if (mbean.getOperations().length <= opIdx) {
      throw new RuntimeException("Operation at index '" + opIdx + "' does not exist!");
    }

    // Get operation and extract parameters:
    MBeanOperation operation = mbean.getOperations()[opIdx];
    String[] params = new String[operation.getParameters().length];
    for (int i = 0; i < params.length; i++) {
      params[i] = request.getParameter(RequestParams.MBEAN_OPERATION_VALUE + i);
    }
    if (request.getParameter(RequestParams.MBEAN_OPERATION_INVOKE) != null) {
      if (!mbeanAccessControl.isCallAllowed(mbean, operation)) {
        throw new UnsupportedOperationException("Not allowed to invoke the operation according to MBeanAccessControl!");
      }

      Message msg;
      MBeanData reloaded = mbean;
      try {
        // Invoke the operation:
        Object result = MBeanUtils.invokeOperation(mbean.getName(), operation, params);
        mbeanAccessControl.operationCalled(mbean, operation, params, result);

        // Reload state & show MBean page:
        reloaded = MBeanUtils.getMBean(mbean.getName());
        String okMsg = "Operation <b>" + operation.getName() + "</b> successfully invoked.";
        if ("void".equals(operation.getReturnType())) {
          msg = new Message(okMsg + " No operation result (void).", MessageType.OK);
        }
        else {
          String resultTxt = result != null ? result.toString() : "null";
          if (resultTxt.indexOf("\n") > 0) {
            resultTxt = "\n" + resultTxt; // If multi-line content, begin result on new line...
          }
          msg = new Message(okMsg + " Operation result: <b>" + Utils.htmlEncode(resultTxt) + "</b>", MessageType.OK);
        }
      }
      catch (Exception e) {
        mbeanAccessControl.operationCallFailed(mbean, operation, params, e);
        msg = new Message(Utils.getHtmlExceptionInfo(e), MessageType.ERROR);
      }
      return new MBeansDetailView(response.getOutput(), reloaded, msg, mbeanAccessControl);
    }
    else if (request.getParameter(RequestParams.MBEAN_OPERATION_INVOKE_CONFIRM) != null) {
      return new MBeansInvokeOperationView(response.getOutput(), mbean, opIdx, operation, ConfirmState.NOW, params);
    }
    else if (request.getParameter(RequestParams.MBEAN_OPERATION_CANCEL) != null) {
      // Show MBean page:
      Message warnMsg = new Message("Canceled, operation <b>" + operation.getName() + "</b> not invoked!", MessageType.WARN);
      return new MBeansDetailView(response.getOutput(), mbean, warnMsg, mbeanAccessControl);
    }
    else {
      // Show page to call the operation:
      ConfirmState confirmState = mbeanAccessControl.needsCallConfirmation(mbean, operation) ? ConfirmState.NEXT : ConfirmState.OFF;
      return new MBeansInvokeOperationView(response.getOutput(), mbean, opIdx, operation, confirmState, params);
    }
  }

  private static String getRefreshParameters(HttpRequest request) {
    StringBuilder params = new StringBuilder();
    for (Map.Entry<String, String> entry : request.getParameters().entrySet()) {
      if (!NO_REFRESH_PARAMS.contains(entry.getKey()) && entry.getValue() != null) {
        if (params.length() > 0) {
          params.append("&amp;");
        }
        params.append(entry.getKey()).append("=").append(Utils.urlEncode(entry.getValue()));
      }
    }
    return params.toString();
  }
}
