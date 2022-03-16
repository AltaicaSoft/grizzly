/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 2009, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.grizzly.servlet;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.net.SocketFactory;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.utils.Futures;
import org.hamcrest.CustomTypeSafeMatcher;
import org.junit.Test;

import junit.framework.AssertionFailedError;

import static jakarta.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Basic Servlet Test.
 *
 * @author Jeanfrancois Arcand
 */
public class BasicServletTest extends HttpServerAbstractTest {

    private static final int PORT = PORT();
    private static final Logger LOGGER = Grizzly.logger(BasicServletTest.class);
    private static final String TEXT_HTML_HEADER = "text/html;charset=utf8";
    private static final Predicate<String> PATTERN_POSITIVE_NUMBER = Pattern.compile("[1-9][0-9]*").asMatchPredicate();

    public void testServletName() throws Exception {
        System.out.println("testServletName");

        try {
            newHttpServer(PORT);
            WebappContext webappContext = new WebappContext("Test", "/contextPath");
            addServlet(webappContext, "foobar", "/servletPath/*");
            webappContext.deploy(httpServer);

            Thread.sleep(100);
            httpServer.start();
            HttpURLConnection connection = getConnection("/contextPath/servletPath/pathInfo", PORT);
            String servletName = connection.getHeaderField("Servlet-Name");
            assertEquals(servletName, "foobar");
        } finally {
            stopHttpServer();
        }
    }

    public void testSetHeaderTest() throws Exception {
        System.out.println("testSetHeaderTest");
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            String alias = "/1";
            addServlet(ctx, "TestServlet", alias);
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection(alias, PORT);
            String s = conn.getHeaderField("Content-Type");
            assertEquals(TEXT_HTML_HEADER, s);
        } finally {
            stopHttpServer();
        }
    }

    public void testPathInfo() throws IOException {
        System.out.println("testPathInfo");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "TestServlet", "/servletPath/*");
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            String s = conn.getHeaderField("Path-Info");
            assertEquals("/pathInfo", s);
        } finally {
            stopHttpServer();
        }
    }

    public void testDoubleSlash() throws IOException {
        System.out.println("testDoubleSlash");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/");
            addServlet(ctx, "TestServet", "*.html");
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/index.html", PORT);
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            String s = conn.getHeaderField("Request-Was");
            System.out.println("s: " + s);
            assertEquals(s, "/index.html");
        } finally {
            stopHttpServer();
        }
    }

    public void testDefaultServletPaths() throws Exception {
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            addServlet(ctx, "TestServlet", "");
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/index.html", PORT);
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            String s = conn.getHeaderField("Request-Was");
            System.out.println("s: " + s);
            assertEquals(s, "/index.html");
        } finally {
            stopHttpServer();
        }
    }

    public void testInvalidServletContextPathSpec() throws Exception {
        try {
            new WebappContext("Test", "/test/");
            fail("Expected IllegalArgumentException to be thrown when context path ends with '/'");
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    public void testInitParameters() throws IOException {
        System.out.println("testContextParameters");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            ctx.addContextInitParameter("ctx", "something");
            ServletRegistration servlet1 = ctx.addServlet("Servlet1", new HttpServlet() {
                private ServletConfig config;

                @Override
                public void init(ServletConfig config) throws ServletException {
                    super.init(config);
                    this.config = config;
                }

                @Override
                protected void service(HttpServletRequest req, HttpServletResponse resp) {
                    String init = config.getInitParameter("servlet");
                    String ctx = config.getServletContext().getInitParameter("ctx");
                    boolean ok = "sa1".equals(init) && "something".equals(ctx);
                    resp.setStatus(ok ? 200 : 404);
                }
            });
            servlet1.setInitParameter("servlet", "sa1");
            servlet1.addMapping("/1");

            ServletRegistration servlet2 = ctx.addServlet("Servlet2", new HttpServlet() {
                private ServletConfig config;

                @Override
                public void init(ServletConfig config) throws ServletException {
                    super.init(config);
                    this.config = config;
                }

                @Override
                protected void service(HttpServletRequest req, HttpServletResponse resp) {
                    String init = config.getInitParameter("servlet");
                    String ctx = config.getServletContext().getInitParameter("ctx");
                    boolean ok = "sa2".equals(init) && "something".equals(ctx);
                    resp.setStatus(ok ? 200 : 404);
                }
            });
            servlet2.setInitParameter("servlet", "sa2");
            servlet2.addMapping("/2");
            ctx.deploy(httpServer);
            httpServer.start();

            assertEquals(200, getConnection("/1", PORT).getResponseCode());
            assertEquals(200, getConnection("/2", PORT).getResponseCode());
        } finally {
            stopHttpServer();
        }
    }

    /**
     * Covers issue with "No Content" returned by Servlet.
     * <a href="http://twitter.com/shock01/status/2136930089">http://twitter.com/shock01/status/2136930089</a>
     *
     * @throws IOException I/O
     */
    public void testNoContentServlet() throws Exception {
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            ServletRegistration reg = ctx.addServlet("TestServlet", new HttpServlet() {
                @Override
                protected void service(HttpServletRequest req, HttpServletResponse resp) {
                    resp.setStatus(SC_NO_CONTENT);
                }
            });
            reg.addMapping("/NoContent");
            ctx.deploy(httpServer);

            assertEquals(SC_NO_CONTENT, getConnection("/NoContent", PORT).getResponseCode());
        } finally {
            stopHttpServer();
        }
    }

    public void testInternalArtifacts() throws Exception {
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            ServletRegistration reg = ctx.addServlet("TestServlet", new HttpServlet() {
                @Override
                protected void service(HttpServletRequest req, HttpServletResponse resp) {
                    Request grizzlyRequest = ServletUtils.getInternalRequest(req);
                    Response grizzlyResponse = ServletUtils.getInternalResponse(resp);

                    resp.addHeader("Internal-Request", grizzlyRequest != null ? "present" : null);
                    resp.addHeader("Internal-Response", grizzlyResponse != null ? "present" : null);
                }
            });
            reg.addMapping("/internal");
            ctx.deploy(httpServer);

            HttpURLConnection connection = getConnection("/internal", PORT);

            assertEquals(HttpServletResponse.SC_OK, connection.getResponseCode());

            assertEquals("present", connection.getHeaderField("Internal-Request"));
            assertEquals("present", connection.getHeaderField("Internal-Response"));
        } finally {
            stopHttpServer();
        }
    }

    public void testContextListener() throws IOException {
        System.out.println("testContextListener");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            ctx.addListener(MyContextListener.class);

            addServlet(ctx, "foobar", "/servletPath/*");
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            String s = conn.getHeaderField("Servlet-Name");
            assertEquals("foobar", s);
        } finally {
            stopHttpServer();

            assertEquals(MyContextListener.INITIALIZED, MyContextListener.events.poll());
            assertEquals(MyContextListener.DESTROYED, MyContextListener.events.poll());
        }
    }

    /**
     * Tests isCommitted().
     *
     * @throws Exception If an unexpected IO error occurred.
     */
    public void testIsCommitted() throws Exception {
        System.out.println("testIsCommitted");
        try {
            FutureImpl<Boolean> resultFuture = Futures.createSafeFuture();

            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("example", "/example");
            ServletRegistration reg = ctx.addServlet("managed", new HttpServlet() {
                @Override
                protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    req.startAsync();
                    resp.isCommitted();
                    resp.sendError(400, "Four hundred");
                    try {
                        resp.isCommitted();
                        resultFuture.result(Boolean.TRUE);
                    } catch (Exception e) {
                        resultFuture.failure(e);
                    }
                }
            });
            reg.addMapping("/managed/*");
            ctx.deploy(httpServer);

            httpServer.start();

            HttpURLConnection conn = getConnection("/example/managed/users", PORT);
            assertEquals(400, conn.getResponseCode());
            assertTrue(resultFuture.get(10, TimeUnit.SECONDS));
        } finally {
            httpServer.shutdownNow();
        }
    }

    /**
     * Related to the issue https://java.net/jira/browse/GRIZZLY-1578
     */
    @Test
    public void testInputStreamMarkReset() throws Exception {
        String param1Name = "j_username";
        String param2Name = "j_password";
        String param1Value = "admin";
        String param2Value = "admin";

        newHttpServer(PORT);
        WebappContext ctx = new WebappContext("example", "/");
        ServletRegistration reg = ctx.addServlet("paramscheck", new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

                try {
                    InputStream is = req.getInputStream();
                    assertTrue(is.markSupported());
                    is.mark(1);
                    assertEquals('j', is.read());
                    is.reset();
                    assertEquals(param1Value, req.getParameter(param1Name));
                    assertEquals(param2Value, req.getParameter(param2Name));
                } catch (Throwable t) {
                    LOGGER.log(Level.SEVERE, "Error", t);
                    resp.sendError(500, t.getMessage());
                }

            }
        });
        reg.addMapping("/");
        ctx.deploy(httpServer);

        Socket s = null;
        try {
            httpServer.start();
            String postHeader = "POST / HTTP/1.1\r\n" + "Host: localhost:" + PORT + "\r\n"
                    + "User-Agent: Mozilla/5.0 (iPod; CPU iPhone OS 5_1_1 like Mac OS X) AppleWebKit/534.46 (KHTML, like Gecko) Version/5.1 Mobile/9B206 Safari/7534.48.3\r\n"
                    + "Content-Length: 33\r\n" + "Accept: */*\r\n" + "Origin: http://192.168.1.165:9998\r\n" + "X-Requested-With: XMLHttpRequest\r\n"
                    + "Content-Type: application/x-www-form-urlencoded; charset=UTF-8\r\n" + "Referer: http://192.168.1.165:9998/\r\n"
                    + "Accept-Language: en-us\r\n" + "Accept-Encoding: gzip, deflate\r\n" + "Cookie: JSESSIONID=716476212401473028\r\n"
                    + "Connection: keep-alive\r\n\r\n";

            String postBody = param1Name + "=" + param1Value + "&" + param2Name + "=" + param2Value;

            s = SocketFactory.getDefault().createSocket("localhost", PORT);
            OutputStream out = s.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

            out.write(postHeader.getBytes());
            out.flush();
            Thread.sleep(100);
            out.write(postBody.getBytes());
            out.flush();

            assertEquals("HTTP/1.1 200 OK", in.readLine());

        } finally {
            httpServer.shutdownNow();
            if (s != null) {
                s.close();
            }
        }

    }

    /**
     * https://java.net/jira/browse/GRIZZLY-1772
     */
    public void testLoadServletDuringParallelRequests() throws Exception {
        System.out.println("testLoadServletDuringParallelRequests");

        InitBlocker blocker = new InitBlocker();
        InitBlockingServlet.setBlocker(blocker);
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("testParallelLoadServlet");
            ServletRegistration servlet = ctx.addServlet("InitBlockingServlet", InitBlockingServlet.class);
            servlet.addMapping("/initBlockingServlet");

            ctx.deploy(httpServer);
            httpServer.start();

            FutureTask<Void> request1 = new FutureTask<>(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    assertEquals(200, getConnection("/initBlockingServlet", PORT).getResponseCode());
                    return null;
                }
            });

            FutureTask<Void> request2 = new FutureTask<>(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    assertEquals(200, getConnection("/initBlockingServlet", PORT).getResponseCode());
                    return null;
                }
            });

            new Thread(request1).start();
            blocker.waitUntilInitCalled();
            new Thread(request2).start();

            try {
                request2.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException ignored) {
                // request2 should block until the servlet instance is initialized by request1
            } finally {
                blocker.releaseInitCall();
            }

            request1.get();
            request2.get();

        } finally {
            try {
                blocker.releaseInitCall();
                InitBlockingServlet.removeBlocker(blocker);
            } finally {
                stopHttpServer();
            }
        }
    }

    public void testServlet6NewMethods() throws Exception {
        try {
            newHttpServer(PORT);
            final String contextPath = "/contextPath";
            WebappContext webappContext = new WebappContext("Test", contextPath);
            ServletRegistration reg = webappContext.addServlet("TestServlet", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                    LOGGER.log(Level.INFO, "{0} received request {1}", new Object[] { contextPath, req.getRequestURI() });
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setHeader("Content-Type", TEXT_HTML_HEADER);
                    resp.setHeader("Path-Info", req.getPathInfo());
                    ServletConnection servletConnection = req.getServletConnection();
                    resp.setHeader("X-SC-ID", servletConnection.getConnectionId());
                    resp.setHeader("X-SC-PROTOCOL", servletConnection.getProtocol());
                    resp.setHeader("X-SC-PROTOCOL_CONNECTION_ID", servletConnection.getProtocolConnectionId());
                    resp.setHeader("X-SC-SECURE", Boolean.toString(servletConnection.isSecure()));
                    resp.setHeader("X-REQUEST_ID", req.getRequestId());
                    resp.setHeader("X-PROTOCOL_REQUEST_ID", req.getProtocolRequestId());
                    resp.getWriter().write(contextPath);
                }
            });
            reg.addMapping(contextPath);
            webappContext.deploy(httpServer);

            Thread.sleep(100);
            httpServer.start();
            HttpURLConnection connection = getConnection("/contextPath/contextPath", PORT);
            assertEquals(HttpServletResponse.SC_OK, connection.getResponseCode());
            assertThat("request id", connection.getHeaderField("X-REQUEST_ID"), new PositiveNumberMatcher());
            assertThat("protocol request id", connection.getHeaderField("X-PROTOCOL_REQUEST_ID"), equalTo(""));
            assertThat("connection id", connection.getHeaderField("X-SC-ID"), new PositiveNumberMatcher());
            assertThat(connection.getHeaderField("X-SC-PROTOCOL"), equalTo(Protocol.HTTP_1_1.getProtocolString()));
            assertThat(connection.getHeaderField("X-SC-PROTOCOL_CONNECTION_ID"), equalTo(""));
            assertThat(connection.getHeaderField("X-SC-SECURE"), equalTo("false"));
        } finally {
            stopHttpServer();
        }
    }

    private ServletRegistration addServlet(WebappContext ctx, String name, String alias) {
        ServletRegistration reg = ctx.addServlet(name, new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                LOGGER.log(Level.INFO, "{0} received request {1}", new Object[] { alias, req.getRequestURI() });
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setHeader("Content-Type", TEXT_HTML_HEADER);
                resp.setHeader("Path-Info", req.getPathInfo());
                resp.setHeader("Request-Was", req.getRequestURI());
                resp.setHeader("Servlet-Name", getServletName());
                resp.getWriter().write(alias);
            }
        });
        reg.addMapping(alias);

        return reg;
    }

    private static class InitBlocker {
        private boolean initReleased, initCalled;

        synchronized void notifyInitCalledAndWaitForRelease() throws InterruptedException {
            assertFalse("init has already been called", initCalled);
            initCalled = true;
            notifyAll();
            while (!initReleased) {
                wait();
            }
        }

        synchronized void releaseInitCall() {
            initReleased = true;
            notifyAll();
        }

        synchronized void waitUntilInitCalled() throws InterruptedException {
            while (!initCalled) {
                wait();
            }
        }
    }

    public static class InitBlockingServlet extends HttpServlet {
        private static AtomicReference<InitBlocker> BLOCKER = new AtomicReference<>();

        private volatile boolean initialized;

        static void setBlocker(InitBlocker blocker) {
            assertNotNull(blocker);
            assertTrue(BLOCKER.compareAndSet(null, blocker));
        }

        static void removeBlocker(InitBlocker blocker) {
            assertTrue(BLOCKER.compareAndSet(blocker, null));
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);

            InitBlocker blocker = BLOCKER.get();
            assertNotNull(blocker);

            try {
                blocker.notifyInitCalledAndWaitForRelease();
            } catch (InterruptedException e) {
                throw (Error) new AssertionFailedError().initCause(e);
            }

            initialized = true;
        }

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) {
            boolean ok = initialized;
            resp.setStatus(ok ? 200 : 500);
        }
    }

    public static class MyContextListener implements ServletContextListener {
        static String INITIALIZED = "initialized";
        static String DESTROYED = "destroyed";

        static Queue<String> events = new ConcurrentLinkedQueue<>();

        public MyContextListener() {
            events.clear();
        }

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            events.add(INITIALIZED);
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            events.add(DESTROYED);
        }
    }


    private static class PositiveNumberMatcher extends CustomTypeSafeMatcher<String> {
        PositiveNumberMatcher() {
            super("positive number");
        }

        @Override
        protected boolean matchesSafely(String value) {
            return PATTERN_POSITIVE_NUMBER.test(value);
        }
    }
}
