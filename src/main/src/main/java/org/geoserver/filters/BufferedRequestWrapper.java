/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.filters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.geotools.util.Converters;

public class BufferedRequestWrapper extends HttpServletRequestWrapper {
    protected HttpServletRequest myWrappedRequest;

    protected byte[] myBuffer;

    protected String charset;
    protected ServletInputStream myStream = null;
    protected BufferedReader myReader = null;
    protected Map<String, List<String>> myParameterMap;
    protected Logger logger = org.geotools.util.logging.Logging.getLogger("org.geoserver.filters");

    public BufferedRequestWrapper(HttpServletRequest req, String charset, byte[] buff) {
        super(req);
        this.myWrappedRequest = req;
        this.myBuffer = buff;
        this.charset = charset;
    }

    public ServletInputStream getInputStream() throws IOException {
        if (myStream == null) {
            if (myReader == null) {
                myStream = new BufferedRequestStream(myBuffer);
            } else {
                throw new IOException("Requesting a stream after a reader is already in use!!");
            }
        }

        return myStream;
    }

    public BufferedReader getReader() throws IOException {
        if (myReader == null) {
            if (myStream == null) {
                myReader =
                        new BufferedReader(
                                new InputStreamReader(
                                        new BufferedRequestStream(myBuffer), charset));
            } else {
                throw new IOException("Requesting a reader after a stream is already in use!!");
            }
        }

        return myReader;
    }

    public String getParameter(String name) {
        parseParameters();
        List<String> allValues = myParameterMap.get(name);
        if (allValues != null && allValues.size() > 0) {
            return allValues.get(0);
        } else return null;
    }

    public Map<String, String[]> getParameterMap() {
        parseParameters();
        Map<String, String[]> toArrays = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : myParameterMap.entrySet()) {
            String[] value = entry.getValue().toArray(new String[0]);
            toArrays.put(entry.getKey(), value);
        }

        return Collections.unmodifiableMap(toArrays);
    }

    public Enumeration<String> getParameterNames() {
        parseParameters();
        return new IteratorAsEnumeration<>(myParameterMap.keySet().iterator());
    }

    public String[] getParameterValues(String name) {
        parseParameters();
        List<String> allValues = myParameterMap.get(name);
        if (allValues != null && allValues.size() > 0) {
            return allValues.toArray(new String[0]);
        } else return null;
    }

    protected void parseParameters() {
        if (myParameterMap != null) return;
        String contentType = myWrappedRequest.getContentType();
        if (myWrappedRequest.getMethod().equals("POST")
                && contentType != null
                && contentType.startsWith("application/x-www-form-urlencoded")) {
            parseFormBody();
        } else {
            Map<String, String[]> superParameters = super.getParameterMap();
            myParameterMap = new HashMap<>();

            for (String key : superParameters.keySet()) {
                Object value = superParameters.get(key);
                if (value instanceof String[]) {
                    myParameterMap.put(key, Arrays.asList(((String[]) value)));
                } else if (!(value instanceof List)) {
                    @SuppressWarnings("unchecked")
                    List<String> converted = Converters.convert(value, List.class);
                    myParameterMap.put(key, converted);
                }
            }
        }
    }

    protected void parseFormBody() {
        myParameterMap = new TreeMap<>();

        // parse the body
        String[] pairs;
        try {
            pairs = new String(myBuffer, charset).split("\\&");
        } catch (UnsupportedEncodingException e) {
            // should not happen
            throw new RuntimeException(e);
        }

        for (String s : pairs) {
            parsePair(s);
        }

        // we should also parse parameters that came into the request thought
        if (myWrappedRequest.getQueryString() != null) {
            pairs = myWrappedRequest.getQueryString().split("\\&");

            for (String pair : pairs) {
                parsePair(pair);
            }
        }
    }

    protected void parsePair(String pair) {
        String[] split = pair.split("=", 2);
        try {
            String key = URLDecoder.decode(split[0], "UTF-8");
            String value = (split.length > 1 ? URLDecoder.decode(split[1], "UTF-8") : "");

            if (!myParameterMap.containsKey(key)) {
                myParameterMap.put(key, new ArrayList<>());
            }

            myParameterMap.get(key).add(value);

        } catch (UnsupportedEncodingException e) {
            logger.severe("Failed to decode form values in LoggingFilter");
            // we have the encoding hard-coded for now so no exceptions should be thrown...
        }
    }

    private static class IteratorAsEnumeration<T> implements Enumeration<T> {
        Iterator<T> it;

        public IteratorAsEnumeration(Iterator<T> it) {
            this.it = it;
        }

        public boolean hasMoreElements() {
            return it.hasNext();
        }

        public T nextElement() {
            return it.next();
        }
    }
}
