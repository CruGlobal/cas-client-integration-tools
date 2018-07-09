# What?

A small set of servlet filters that help integrate CAS with servlet-based enterprise tools.

# Why?

Some enterprise tools expect SSO solutions to populate certain headers to identify the user,
and the java CAS client doesn't do this out-of-the-box.
Also, other bits of 'glue' are often required to get single-logout working correctly.


# How?

Using maven?
Build this repo (`mvn install`), and add this dependency to your pom:

```xml
  <dependency>
    <groupId>org.cru.cas</groupId>
    <artifactId>cas-client-integration-tools</artifactId>
    <version>1</version>
  </dependency>
```

Not using maven?
Grab the jar from
[github](https://github.com/CruGlobal/cas-client-integration-tools/releases/tag/1)
and get it into your WEB-INF/lib directory.

Use one or more of the following tools by adding these filteres to your web.xml.

## Populate a specific http header with the username

This doesn't use anything cas-specific;
it simply uses `request.getRemoteUser()` to get the username.

```xml
  <filter>

     <!-- adapt as you'd like -->
    <filter-name>Copy User to Header Filter</filter-name>

    <filter-class>org.cru.userheader.CopyUserToHeaderFilter</filter-class>

    <!-- optional; default is 'X-Remote-User' -->
    <init-param>
      <param-name>headerName</param-name>

      <!-- use whatever header name your application expects -->
      <param-value>Some-User-Header-Name</param-value>
    </init-param>
  </filter>

  <filter-mapping>
    <!-- match the <filter-name> above -->
    <filter-name>Copy User to Header Filter</filter-name>

    <!-- adapt as required; this is probably good enough for most applications -->
    <url-pattern>/*</url-pattern>
  </filter-mapping>
```

## Populate a set of http headers with CAS attributes

```xml
  <filter>

    <filter-name>Copy Cas Attributes to Headers Filter</filter-name>
    <filter-class>org.cru.userheader.CopyCasAttributesToHeadersFilter</filter-class>

    <!--
      Optional; default behavior will map all attributes,
      mapping each attribute to a header of the same name prefixed by "CAS_".
     -->
    <init-param>
      <param-name>attributeMapping</param-name>

      <!-- A whitespace-separated list of attribute=header mappings -->
      <param-value>
        someCasAttribute=A-Specific-Header-Used-By-Your-Tool
        someOtherAttribute=Some-Other-Header
      </param-value>
    </init-param>
  </filter>

  <filter-mapping>
    <filter-name>Copy Cas Attributes to Headers Filter</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>
```

Any Collection attributes will be converted to multiple headers.
Any non-String attributes will be converted to Strings using their `toString()` method.

Note: if an attribute mapping is not specified,
the header names will not be case-insensitive, as header names often are expected to be.


# Misc

This was built to integrate Cru's siebel and peoplesoft instances with our CAS server.
