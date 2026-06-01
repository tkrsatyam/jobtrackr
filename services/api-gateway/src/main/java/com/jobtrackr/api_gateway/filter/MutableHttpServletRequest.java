package com.jobtrackr.api_gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.*;

public class MutableHttpServletRequest extends HttpServletRequestWrapper {

    private final Map<String, String> customHeaders = new HashMap<>();

    public MutableHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    public void putHeader(String name, String value) {
        customHeaders.put(name, value);
    }

    @Override
    public String getHeader(String name) {
        String value = customHeaders.get(name);
        return value != null ? value : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String customValue = customHeaders.get(name);
        if (customValue != null) {
            return Collections.enumeration(List.of(customValue));
        }
        Enumeration<String> original = super.getHeaders(name);
        return original != null ? original : Collections.emptyEnumeration();
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = Collections.list(super.getHeaderNames());
        names.removeIf(name -> super.getHeader(name) == null);
        names.addAll(customHeaders.keySet());
        return Collections.enumeration(names);
    }
}
