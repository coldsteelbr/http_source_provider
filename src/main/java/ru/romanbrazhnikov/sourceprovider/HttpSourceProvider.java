package ru.romanbrazhnikov.sourceprovider;

import io.reactivex.Single;
import org.apache.commons.lang3.StringEscapeUtils;
import ru.romanbrazhnikov.sourceprovider.cookies.Cookie;
import ru.romanbrazhnikov.circular_queue.CircularQueue;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class HttpSourceProvider {
    static {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
    }

    // TIME OUT
    private static final int READ_TIME_OUT_MS = 15000;
    private static final int CONNECT_TIME_OUT_MS = 20000;

    // UnEscape \\uXXXX string to normal chars
    boolean mDoUnEscape = false;

    // TODO: Add encoding conversion
    // TODO: from server: ByteBuffer byteBuffer = Charset.forName("UTF-8").encode(myString)
    private String mBaseUrl = "";
    private String mUrlDelimiter = "";

    // PROXY
    private boolean mUseProxy = false;
    private CircularQueue<IpPort> mProxyList;
    // TODO: LOAD PROXY LIST

    // Cookies
    private List<String> mCookiesToRequest;
    private List<String> mCookiesFromResponse;

    // Headers
    private Map<String, String> mHeaders;

    private String mSourceEncoding = "utf8";
    private HttpMethods mHttpMethod = HttpMethods.GET;
    private String mQueryParamString;

    public void setProxyList(CircularQueue<IpPort> proxyList) {
        mProxyList = proxyList;
        mUseProxy = mProxyList != null;
    }

    public void setBaseUrl(String baseUrl) {
        mBaseUrl = baseUrl;
    }

    public void setUrlDelimiter(String urlDelimiter) {
        mUrlDelimiter = urlDelimiter;
    }

    public void setSourceEncoding(String sourceEncoding) {
        switch (sourceEncoding.toLowerCase().replaceAll("\\s", "")) {
            case "utf-8":
            case "utf8":
                mSourceEncoding = "UTF-8";
                break;
            case "windows-1251":
            case "windows1251":
            case "cp-1251":
            case "cp1251":
            case "1251":
                mSourceEncoding = "Windows-1251";
                break;
            default:
                mSourceEncoding = sourceEncoding;
        }

    }

    public void setHttpMethod(HttpMethods httpMethod) {
        mHttpMethod = httpMethod;
    }

    public void setQueryParamString(String queryParamString) {
        mQueryParamString = queryParamString;
    }

    public Single<String> requestSource() {

        return Single.create(emitter -> {
            try {
                // Proxy
                Proxy proxy = null;
                if (mProxyList != null && mProxyList.getSize() > 0 && mUseProxy) {
                    IpPort ipPort = mProxyList.reuse();
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ipPort.getIp(), ipPort.getPort()));
                }

                // opening connection
                URL myURL = null;// = new URL(mBaseUrl);
                HttpURLConnection httpConnection = null;// = (HttpURLConnection) myURL.openConnection();


                // METHOD
                switch (mHttpMethod) {

                    case GET:
                        myURL = new URL(mBaseUrl + (mQueryParamString != null ? mUrlDelimiter + mQueryParamString : ""));
                        // USE PROXY OR NOT
                        if (proxy != null) {
                            httpConnection = (HttpURLConnection) myURL.openConnection(proxy);
                        } else {
                            httpConnection = (HttpURLConnection) myURL.openConnection();
                        }
                        httpConnection.setReadTimeout(READ_TIME_OUT_MS);
                        httpConnection.setConnectTimeout(CONNECT_TIME_OUT_MS);
                        addHeadersIfAny(httpConnection);
                        addCookiesIfAny(httpConnection);
                        break;
                    case POST:
                        myURL = new URL(mBaseUrl);

                        // USE PROXY OR NOT
                        if (proxy != null) {
                            httpConnection = (HttpURLConnection) myURL.openConnection(proxy);
                        } else {
                            httpConnection = (HttpURLConnection) myURL.openConnection();
                        }
                        httpConnection.setDoOutput(true);// Triggers POST.

                        if (mHeaders != null) {
                            addHeadersIfAny(httpConnection);
                        } else {
                            httpConnection.setRequestProperty("User-Agent", "");
                            httpConnection.setRequestProperty("Accept", "application/json, text/plain, */*");
                            httpConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + StandardCharsets.UTF_8.displayName());
                        }

                        httpConnection.setInstanceFollowRedirects(false);
                        httpConnection.setDoOutput(true);
                        httpConnection.setReadTimeout(READ_TIME_OUT_MS);
                        httpConnection.setConnectTimeout(CONNECT_TIME_OUT_MS);
                        httpConnection.setRequestMethod("POST");


                        addCookiesIfAny(httpConnection);

                        // Sending POST form

                        DataOutputStream out = null;
                        byte[] postData = mQueryParamString.trim().getBytes(StandardCharsets.UTF_8);
                        out = new DataOutputStream(httpConnection.getOutputStream());
                        out.write(postData);
                        out.flush();
                        out.close();
                        break;
                }

                System.out.println(myURL.toString());

                // getting response
                //int status = httpConnection.getResponseCode();

                // reading response body according encoding
                BufferedReader bReader;
                if ("gzip".equals(httpConnection.getContentEncoding())) {
                    bReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(httpConnection.getInputStream()), mSourceEncoding));
                } else {
                    bReader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream(), mSourceEncoding));
                }
                String currentString = "";
                StringBuilder httpResponseStringBuilder = new StringBuilder();

                // building response string
                while ((currentString = bReader.readLine()) != null) {
                    httpResponseStringBuilder.append(currentString).append("\n");
                }

                bReader.close();

                // getting headers
                Set<String> httpHeaders = httpConnection.getHeaderFields().keySet();

                // getting cookie headers
                if (httpHeaders.contains("Set-Cookie")) {
                    mCookiesFromResponse = httpConnection.getHeaderFields().get("Set-Cookie");
                }

                // SUCCESS CASE
                String unescapedResponse = mDoUnEscape ?
                        StringEscapeUtils.unescapeJava(httpResponseStringBuilder.toString()) :
                        httpResponseStringBuilder.toString();

                emitter.onSuccess(unescapedResponse);

            } catch (Exception ex) {
                // ERROR CASE
                Exception exception = new Exception("HttpSourceProvider (requestSource): " + ex.getMessage());
                exception.setStackTrace(ex.getStackTrace());
                emitter.onError(exception);
            }
        });
    }

    public void setHeaders(Map<String, String> headers) {
        mHeaders = headers;
    }

    private void addHeadersIfAny(HttpURLConnection httpConnection) {
        if (mHeaders != null) {
            for (Map.Entry<String, String> currentHeader : mHeaders.entrySet()) {
                httpConnection.setRequestProperty(currentHeader.getKey(), currentHeader.getValue());
            }
        }
    }

    private void addCookiesIfAny(HttpURLConnection httpConnection) {
        // Adding cookies if any
        if (mCookiesToRequest != null) {
            for (String currentCookie : mCookiesToRequest) {
                httpConnection.setRequestProperty("Cookie", currentCookie);
            }
        }
    }

    public void setCookiesHeadersToRequest(List<String> cookiesToRequest) {
        mCookiesToRequest = cookiesToRequest;
    }

    public void setCustomCookies(List<Cookie> cookieList) {
        // lazy initialization
        if (mCookiesToRequest == null) {
            mCookiesToRequest = new ArrayList<>();
        }

        // setting cookies
        for (Cookie currentCookie : cookieList) {
            mCookiesToRequest.add(currentCookie.getHeader());
        }

    }

    public List<String> getCookieHeadersFromResponse() {
        return mCookiesFromResponse;
    }
}
